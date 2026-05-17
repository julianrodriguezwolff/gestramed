# gestramed

Aplicación Android básica en Kotlin con dos flujos:

- Órdenes
- Autorizaciones

Cada flujo permite seleccionar un archivo PDF o JPEG y subirlo a un bucket S3 con prefijo distinto:

- `ordenes/`
- `autorizaciones/`

## Configuración

Requisitos locales:

- JDK 17 con `JAVA_HOME` configurado.
- Android SDK instalado (Android Studio o Command-line Tools) y ruta SDK disponible.

1. Abre el proyecto en Android Studio.
2. En Android Studio, abre SDK Manager e instala:
	- Android SDK Platform 34
	- Android SDK Build-Tools 34.0.0
	- Android SDK Platform-Tools
2. Crea o edita `local.properties` en la raíz del proyecto con:

```properties
AWS_ACCESS_KEY=TU_ACCESS_KEY
AWS_SECRET_KEY=TU_SECRET_KEY
AWS_REGION=us-east-1
S3_BUCKET=nombre-del-bucket
```

3. Crea o edita `local.properties` en la raíz del proyecto con:

```properties
sdk.dir=C:\\Users\\jarodriguezw\\AppData\\Local\\Android\\Sdk
AWS_ACCESS_KEY=TU_ACCESS_KEY
AWS_SECRET_KEY=TU_SECRET_KEY
AWS_REGION=us-east-1
S3_BUCKET=nombre-del-bucket
```

4. Sincroniza Gradle.
5. Ejecuta la app en un emulador o dispositivo.

Comando de build:

```bash
./gradlew.bat assembleDebug
```

## Notas de seguridad

- No subas `local.properties` al repositorio.
- Para producción, evita llaves estáticas en cliente móvil.
- Recomendado: usar Cognito Identity Pool o backend firmado para credenciales temporales.

## Flujo funcional

1. Pulsa `Órdenes` o `Autorizaciones`.
2. Selecciona un PDF o JPEG.
3. La app sube el archivo a S3 con la ruta adecuada.
