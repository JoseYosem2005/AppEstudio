# Mis estudios

Gestor de estudios para Android (HTML + Capacitor): asignaturas, temas, bibliografía y tareas pendientes.

## Cómo funciona

- **Asignatura** → pestañas *Temas*, *Bibliografía* y *Tareas*.
- **Tema** → *Guías y medios de estudio*, más la bibliografía de la asignatura en una sección aparte.
- **Tareas** → fecha límite opcional; se completan entregando archivos (o marcándolas hechas).
- En el APK, todo vive en **una carpeta que eliges tú** (permiso guardado: al abrir la app todo está cargado):
  `Asignatura/Bibliografía`, `Asignatura/Temas/<tema>`, `Asignatura/Tareas`, `Asignatura/Entregas` y `estudios.json`.
  Al abrir, la app añade lo que hayas dejado en la carpeta y quita lo que hayas borrado.
- En el navegador funciona con IndexedDB (con exportar/importar respaldo).

## Estructura del repo

```
www/index.html                          la app
android-plugin/StudyFolderPlugin.java   plugin propio (selector de carpeta con permiso persistente)
scripts/install-studyfolder.sh          copia y registra el plugin tras `npx cap add android`
capacitor.config.json  package.json  assets/icon.svg
.github/workflows/build-android.yml     compila el APK (también con "Run workflow")
```

## APK

Cada push a `main` lanza *Build Android APK*. El APK queda como artifact `estudios-debug-apk` en la pestaña **Actions**.
La primera vez que abras la app te pedirá elegir la carpeta.
