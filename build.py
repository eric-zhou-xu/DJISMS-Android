from pathlib import Path
import os, subprocess, zipfile, shutil
root=Path(__file__).resolve().parent
sdk=Path(os.environ.get('ANDROID_HOME') or os.environ['ANDROID_SDK_ROOT'])
jdk=Path(os.environ['JAVA_HOME'])
bt=sdk/'build-tools/35.0.0'; android=sdk/'platforms/android-35/android.jar'
out=root/'build'; dist=root/'dist'; out.mkdir(exist_ok=True); dist.mkdir(exist_ok=True)
classes=out/'classes'; dex=out/'dex'
for p in (classes,dex):
 if p.exists(): shutil.rmtree(p)
 p.mkdir()
def run(a): subprocess.run([str(x) for x in a],check=True)
run([bt/'aapt2','compile','--dir',root/'res','-o',out/'resources.zip'])
run([bt/'aapt2','link','-o',out/'base.apk','--manifest',root/'AndroidManifest.xml','-I',android,'--min-sdk-version','26','--target-sdk-version','35','-R',out/'resources.zip'])
run([jdk/'bin/javac','-encoding','UTF-8','-source','8','-target','8','-classpath',android,'-d',classes,*root.glob('src/**/*.java')])
with zipfile.ZipFile(out/'classes.jar','w') as z:
 for p in classes.rglob('*.class'): z.write(p,p.relative_to(classes))
run([bt/'d8','--lib',android,'--min-api','26','--output',dex,out/'classes.jar'])
with zipfile.ZipFile(out/'base.apk','a') as z:
 for p in dex.glob('*.dex'): z.write(p,p.name)
run([bt/'zipalign','-f','4',out/'base.apk',dist/'DJISMS-Android-unsigned.apk'])
print(dist/'DJISMS-Android-unsigned.apk')
