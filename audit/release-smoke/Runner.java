package cz.jan.reader.releasesmoke;
import android.app.Instrumentation;
import android.content.*;
import android.os.*;
import android.view.accessibility.AccessibilityNodeInfo;
import org.json.*;
import java.io.*;
import java.nio.charset.StandardCharsets;

public class Runner extends Instrumentation {
  private Bundle arguments;

  @Override public void onCreate(Bundle b) { super.onCreate(b); arguments=b; start(); }
  private AccessibilityNodeInfo find(AccessibilityNodeInfo n, String label) {
    if(n==null) return null;
    if(label.contentEquals(n.getText()==null?"":n.getText()) || label.contentEquals(n.getContentDescription()==null?"":n.getContentDescription())) return n;
    for(AccessibilityNodeInfo.AccessibilityAction a:n.getActionList())
      if(a.getId()==AccessibilityNodeInfo.ACTION_CLICK && label.contentEquals(a.getLabel()==null?"":a.getLabel())) return n;
    for(int i=0;i<n.getChildCount();i++){ AccessibilityNodeInfo r=find(n.getChild(i),label); if(r!=null)return r; }
    return null;
  }
  private AccessibilityNodeInfo waitFor(String label) {
    for(int i=0;i<100;i++){ AccessibilityNodeInfo n=find(getUiAutomation().getRootInActiveWindow(),label); if(n!=null)return n; SystemClock.sleep(100); }
    throw new AssertionError("Missing control: "+label);
  }
  private void click(String label) {
    AccessibilityNodeInfo n=waitFor(label);
    while(n!=null&&!n.isClickable()) n=n.getParent();
    if(n==null||!n.performAction(AccessibilityNodeInfo.ACTION_CLICK)) throw new AssertionError("Cannot click: "+label);
    SystemClock.sleep(450); waitForIdleSync();
  }
  private boolean expandPanel(AccessibilityNodeInfo node) {
    if(node==null) return false;
    for(AccessibilityNodeInfo.AccessibilityAction action:node.getActionList())
      if(action.getId()==AccessibilityNodeInfo.ACTION_EXPAND && node.performAction(action.getId())) return true;
    for(int i=0;i<node.getChildCount();i++) if(expandPanel(node.getChild(i))) return true;
    return false;
  }
  private boolean scrollForward(AccessibilityNodeInfo node) {
    if(node==null) return false;
    if(node.isScrollable() && node.performAction(AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)) return true;
    for(int i=0;i<node.getChildCount();i++) if(scrollForward(node.getChild(i))) return true;
    return false;
  }
  private void reveal(String label) {
    for(int i=0;i<16;i++) {
      AccessibilityNodeInfo root=getUiAutomation().getRootInActiveWindow();
      if(find(root,label)!=null) return;
      if(!scrollForward(root)) break;
      SystemClock.sleep(250);
    }
    waitFor(label);
  }
  private void openReader(Context context) {
    startActivitySync(new Intent(Intent.ACTION_MAIN).setClassName(context.getPackageName(),"cz.jan.conversationreader.MainActivity").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK));
  }
  private void seedUpdate(SharedPreferences prefs) throws JSONException {
    String reference=new JSONObject().put("id","smoke").toString();
    String anchor=new JSONObject().put("message",2).put("openGroups",new JSONArray().put(2)).toString();
    String recent=new JSONArray().put(new JSONObject().put("reference",reference).put("title","Release smoke conversation")
        .put("excerpt","Neutral prompt 1").put("openedAt",System.currentTimeMillis())).toString();
    if(!prefs.edit().putBoolean("bookmarked_smoke",true).putFloat("font",21f).putString("readingFont","Serif")
        .putString("resumeAnchor_smoke",anchor).putString("readingRecent",recent).commit()) throw new AssertionError("Could not seed update state");
  }
  private void verifyUpdate(Context context, SharedPreferences prefs) throws Exception {
    if(!new File(context.getFilesDir(),"conversations/smoke.json").isFile() || !prefs.getBoolean("bookmarked_smoke",false)
        || prefs.getFloat("font",0f)!=21f || !"Serif".equals(prefs.getString("readingFont",null))) {
      throw new AssertionError("The in-place update did not retain the fixture or reading preferences");
    }
    JSONObject anchor=new JSONObject(prefs.getString("resumeAnchor_smoke","{}"));
    if(anchor.getInt("message")!=2 || anchor.getJSONArray("openGroups").getInt(0)!=2) throw new AssertionError("Saved reading position changed before launch");
    openReader(context);
    click("Continue reading"); waitFor("Reading preferences"); waitFor("Remove bookmark"); waitFor("Neutral prompt 1");
  }
  @Override public void onStart() {
    Bundle result=new Bundle();
    try {
      Context c=getTargetContext();
      if(!c.getPackageName().equals("cz.jan.conversationreader.quality"))throw new AssertionError("Requires isolated app");
      int versionCode=c.getPackageManager().getPackageInfo(c.getPackageName(),0).versionCode;
      int expectedCode=Integer.parseInt(arguments.getString("expectedCode",String.valueOf(versionCode)));
      if(versionCode!=expectedCode) throw new AssertionError("Expected version code " + expectedCode + " but found " + versionCode);
      SharedPreferences prefs=c.getSharedPreferences("reader",0);
      String mode=arguments.getString("mode", "smoke");
      if("verify-update".equals(mode)) {
        verifyUpdate(c,prefs);
        result.putString("stream","PASS: in-place upgrade retained the imported conversation, bookmark, font, text size and reading position.\n");
        finish(-1,result);
        return;
      }
      if(!"smoke".equals(mode) && !"seed-update".equals(mode)) throw new AssertionError("Unknown smoke mode");
      prefs.edit().clear().commit();
      File dir=new File(c.getFilesDir(),"conversations"); dir.mkdirs();
      File[] old=dir.listFiles(); if(old!=null)for(File f:old) f.delete();
      JSONArray messages=new JSONArray();
      for(int i=0;i<3;i++){
        messages.put(new JSONObject().put("versions",new JSONArray().put(new JSONObject().put("role","user").put("content","Neutral prompt "+i))));
        messages.put(new JSONObject().put("versions",new JSONArray().put(new JSONObject().put("role","assistant").put("senderInfo",new JSONObject().put("senderName","Synthetic/Release-Model")).put("content","<think>**Plan** the answer.\n\n- Check details.</think>\n\n# Reply "+i+"\n\n**Bold sample** with ordinary text.\n\n| Item | Value |\n| --- | --- |\n| A | B |\n\n```text\ncode sample\n```\n\n"+"A neutral paragraph.\n\n".repeat(30)))));
      }
      try(FileOutputStream out=new FileOutputStream(new File(dir,"smoke.json"))){out.write(new JSONObject().put("name","Release smoke conversation").put("messages",messages).toString().getBytes(StandardCharsets.UTF_8));}
      if("seed-update".equals(mode)) {
        seedUpdate(prefs);
        result.putString("stream","PASS: seeded synthetic data in the baseline app.\n");
        finish(-1,result);
        return;
      }
      openReader(c);
      click("Release smoke conversation"); waitFor("Reading preferences");
      click("Expand prompt and replies");
      for(int i=0;i<2;i++)click("Next prompt");
      for(int i=0;i<2;i++)click("Previous prompt");
      click("Show thinking"); reveal("↑ Collapse thinking"); click("↑ Collapse thinking");
      for(String font:new String[]{"Liberation\nSerif","Courier\nPrime","Sans-\nserif"}){
        click("Reading preferences");
        waitFor("Reading preferences");
        expandPanel(getUiAutomation().getRootInActiveWindow());
        reveal(font);
        click(font); sendKeyDownUpSync(android.view.KeyEvent.KEYCODE_BACK); waitFor("Reading preferences");
      }
      click("Search"); waitFor("Exit search"); click("Exit search");
      waitFor("Reading preferences"); click("Back"); waitFor("Expand prompt and replies");
      click("Back"); waitFor("Conversation Reader");
      result.putString("stream","PASS: optimized APK launch, synthetic JSON, Markdown/table/code, prompt jumps, Thinking, all fonts, search and Back.\n"); finish(-1,result);
    }catch(Throwable t){result.putString("stream","FAIL: "+t.toString()+"\n");finish(0,result);}
  }
}

