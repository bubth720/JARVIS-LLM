package fr.jarvis.a01b;

import android.app.Activity;
import android.app.AlertDialog;
import android.os.Bundle;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.KeyStore;
import java.security.MessageDigest;
import java.security.PrivateKey;
import java.security.Signature;
import java.security.cert.Certificate;
import java.util.Locale;

public final class KeyTestActivity extends Activity {
 private static final String ALIAS="jarvis_a01b_ephemeral_ed25519_test_v1";
 private TextView status;
 private String hex(byte[] bytes) {
  StringBuilder b=new StringBuilder();
  for(byte v:bytes) b.append(String.format(Locale.ROOT,"%02x",v&255));
  return b.toString();
 }
 private KeyStore store() throws Exception {
  KeyStore k=KeyStore.getInstance("AndroidKeyStore");k.load(null);return k;
 }
 private void show(String message) {status.setText(message);}
 private void createTestKey() {
  try {
   KeyStore k=store();
   if(k.containsAlias(ALIAS)) {show("Cle de test deja presente. Supprime-la avant un nouvel essai.");return;}
   KeyPairGenerator g=KeyPairGenerator.getInstance("Ed25519","AndroidKeyStore");
   g.initialize(new KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_SIGN|KeyProperties.PURPOSE_VERIFY)
     .setDigests(KeyProperties.DIGEST_NONE).build());
   KeyPair pair=g.generateKeyPair();
   byte[] challenge="JARVIS-A01B-LOCAL-KEY-TEST-V1".getBytes(java.nio.charset.StandardCharsets.US_ASCII);
   Signature sign=Signature.getInstance("Ed25519");
   sign.initSign(pair.getPrivate());sign.update(challenge);byte[] signature=sign.sign();
   Signature verify=Signature.getInstance("Ed25519");
   verify.initVerify(pair.getPublic());verify.update(challenge);
   boolean valid=verify.verify(signature);
   PrivateKey privateKey=pair.getPrivate();
   boolean notExportable=privateKey.getEncoded()==null;
   byte[] pub=pair.getPublic().getEncoded();
   String fp=hex(MessageDigest.getInstance("SHA-256").digest(pub));
   show("Test local termine.\nSignature verifiee : "+valid+
    "\nPrivateKey.getEncoded()==null : "+notExportable+
    "\nEmpreinte SHA256 de la cle publique SPKI :\n"+fp+
    "\n\nLa cle de TEST reste dans Android Keystore sous un alias local. Aucune cle n'est transmise. "+
    "Ce test ne prouve pas une protection StrongBox/TEE ni une attestation materielle. "+
    "Supprime la cle de test avec le bouton ci-dessous.");
  } catch(Exception e) {
   show("Test refuse ou incompatible : "+e.getClass().getSimpleName()+
    "\nAucune conclusion sur la protection materielle. Utilise Supprimer pour nettoyer une eventuelle cle partielle.");
  }
 }
 private void deleteTestKey() {
  try {
   KeyStore k=store();
   if(k.containsAlias(ALIAS))k.deleteEntry(ALIAS);
   show("Cle de test absente ou supprimee. Aucune cle JARVIS de production creee.");
  } catch(Exception e) {show("Suppression non confirmee : "+e.getClass().getSimpleName());}
 }
 @Override public void onCreate(Bundle state) {
  super.onCreate(state);
  LinearLayout content=new LinearLayout(this);content.setOrientation(LinearLayout.VERTICAL);content.setPadding(24,24,24,24);
  status=new TextView(this);status.setTextSize(16f);status.setTextIsSelectable(true);
  show("A01-B — test local facultatif. Aucune cle creee a l'ouverture.\n"+
   "Appuyer sur Creer declenche une creation de cle Ed25519 de TEST dans Android Keystore. "+
   "La cle reste sur cet appareil. Ne communique jamais de cle privee.");
  Button create=new Button(this);create.setText("Creer la cle de TEST");
  create.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Confirmer le test Ed25519")
    .setMessage("Creer maintenant une cle de test dans Android Keystore ? Elle restera sur l'appareil et pourra etre supprimee. Aucune cle de production ne sera creee.")
    .setNegativeButton("Annuler",null).setPositiveButton("Creer", (d,w)->createTestKey()).show());
  Button delete=new Button(this);delete.setText("Supprimer la cle de TEST");
  delete.setOnClickListener(v->new AlertDialog.Builder(this).setTitle("Supprimer la cle de test")
    .setMessage("Supprimer definitivement la cle de test locale ?")
    .setNegativeButton("Annuler",null).setPositiveButton("Supprimer",(d,w)->deleteTestKey()).show());
  content.addView(status);content.addView(create);content.addView(delete);
  ScrollView scroll=new ScrollView(this);scroll.addView(content);setContentView(scroll);
 }
}
