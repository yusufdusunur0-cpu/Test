#!/bin/bash
# Gradle 8.5 wrapper jar'ı indir
GRADLE_VERSION="8.5"
WRAPPER_JAR="gradle/wrapper/gradle-wrapper.jar"

# Maven Central'den indir
echo "Gradle $GRADLE_VERSION wrapper jar'ı indiriliyor..."
curl -sL "https://repo.maven.apache.org/maven2/org/gradle/gradle-wrapper/$GRADLE_VERSION/gradle-wrapper-$GRADLE_VERSION.jar" -o "$WRAPPER_JAR"

if file "$WRAPPER_JAR" | grep -q "Java"; then
    echo "✅ Başarılı: $WRAPPER_JAR indirildi"
    ls -lh "$WRAPPER_JAR"
else
    echo "❌ Hata: Dosya geçersiz"
    cat "$WRAPPER_JAR" | head -c 100
fi
