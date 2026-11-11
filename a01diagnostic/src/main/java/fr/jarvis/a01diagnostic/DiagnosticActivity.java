package fr.jarvis.a01diagnostic;
import android.app.Activity;
import android.app.ActivityManager;
import android.os.Bundle;
import android.os.Build;
import android.widget.ScrollView;
import android.widget.TextView;
import java.security.Provider;
import java.security.Security;
import java.util.ArrayList;
import java.util.List;
public final class DiagnosticActivity extends Activity {
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  List<String> ed=new ArrayList<>();
  boolean keystore=false;
  for(Provider p:Security.getProviders()) {
   if("AndroidKeyStore".equals(p.getName())) keystore=true;
   for(Provider.Service s:p.getServices()) if("KeyPairGenerator".equalsIgnoreCase(s.getType()) &&
    ("Ed25519".equalsIgnoreCase(s.getAlgorithm())||"EdDSA".equalsIgnoreCase(s.getAlgorithm()))) ed.add(p.getName()+":"+s.getAlgorithm());
  }
  ActivityManager.MemoryInfo mi=new ActivityManager.MemoryInfo();
  ((ActivityManager)getSystemService(ACTIVITY_SERVICE)).getMemoryInfo(mi);
  Runtime rt=Runtime.getRuntime();
  String msg="JARVIS A01 - diagnostic lecture seule\n\nAndroid "+Build.VERSION.RELEASE+" API "+Build.VERSION.SDK_INT+
   "\nAppareil "+Build.MANUFACTURER+" "+Build.MODEL+
   "\nAndroidKeyStore present : "+keystore+
   "\nEd25519/EdDSA KeyPairGenerator : "+ed+
   "\nTas JVM utilise : "+((rt.totalMemory()-rt.freeMemory())/1048576)+" Mio"+
   "\nTas JVM maximum : "+(rt.maxMemory()/1048576)+" Mio"+
   "\nRAM disponible : "+(mi.availMem/1048576)+" Mio"+
   "\nRAM basse : "+mi.lowMemory+
   "\n\nCe test ne prouve pas la non-exportabilite materielle. Aucune cle creee, aucun modele charge.";
  TextView t=new TextView(this);t.setText(msg);t.setTextSize(16);t.setPadding(28,28,28,28);t.setTextIsSelectable(true);
  ScrollView scroll=new ScrollView(this);scroll.addView(t);setContentView(scroll);
 }
}
