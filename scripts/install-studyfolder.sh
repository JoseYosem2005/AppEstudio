#!/usr/bin/env bash
# Instala el plugin StudyFolder en el proyecto Android de Capacitor.
# Ejecutar desde la raíz del repo, DESPUÉS de `npx cap add android` / `npx cap sync android`.
# Es idempotente: se puede ejecutar en cada build.
set -euo pipefail

# Interruptor de diagnóstico: si existe scripts/SKIP_PLUGIN no se instala el plugin.
if [ -f scripts/SKIP_PLUGIN ]; then
  echo "scripts/SKIP_PLUGIN presente: NO se instala el plugin (build de diagnóstico)"
  exit 0
fi

MAIN=$(find android/app/src/main/java -name MainActivity.java | head -n1)
[ -n "$MAIN" ] || { echo "No se encontró MainActivity.java (¿existe la carpeta android/?)"; exit 1; }
DIR=$(dirname "$MAIN")
PKG=$(grep -m1 '^package ' "$MAIN" | sed 's/^package \(.*\);.*/\1/')

# 1) Copiar el plugin con el package correcto
sed "s/__PKG__/$PKG/" android-plugin/StudyFolderPlugin.java > "$DIR/StudyFolderPlugin.java"

# 2) Registrar el plugin en MainActivity (se reescribe: la de Capacitor es de 1 línea)
cat > "$MAIN" <<JAVA
package $PKG;

import android.os.Bundle;
import com.getcapacitor.BridgeActivity;

public class MainActivity extends BridgeActivity {
    @Override
    public void onCreate(Bundle savedInstanceState) {
        registerPlugin(StudyFolderPlugin.class);
        super.onCreate(savedInstanceState);
    }
}
JAVA

# 3) Dependencia de DocumentFile
GRADLE=android/app/build.gradle
grep -q 'androidx.documentfile' "$GRADLE" || \
  sed -i '/^dependencies {/a\    implementation "androidx.documentfile:documentfile:1.0.1"' "$GRADLE"

echo "StudyFolder instalado (package $PKG)"
