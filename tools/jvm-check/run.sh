#!/usr/bin/env bash
# Compiles the app's Android-free Kotlin (text clamps, file names, Office converters, PDF tools) with the
# stand-alone Kotlin compiler and runs the JVM unit tests plus real-PDFBox checks of PdfTools, then converts
# the bundled Office samples to HTML. Works without the Android SDK or Google's Maven, which this repo's
# cloud sessions cannot reach. Jars come from Maven Central into tools/jvm-check/lib (git-ignored).
#
# pdfbox-android is a port of Apache PDFBox 2.0.27, so PdfTools is compiled against org.apache.pdfbox with
# the package name swapped; android.util.Xml is stubbed with kxml2.
set -euo pipefail
HERE=$(cd "$(dirname "$0")" && pwd)
ROOT=$(cd "$HERE/../.." && pwd)
LIB=$HERE/lib; OUT=$HERE/build; GEN=$OUT/gen
P=$ROOT/app/src/main/java/com/whats/web/scan/webscan/pdfreaderpdffileedit
T=$ROOT/app/src/test/java/com/whats/web/scan/webscan/pdfreaderpdffileedit
M=https://repo.maven.apache.org/maven2
mkdir -p "$LIB"
fetch() { local path=$1 file; file=$LIB/$(basename "$path").jar
  [ -s "$file" ] && return 0
  for i in 1 2 3 4; do curl -sSfL -o "$file" "$M/$path.jar" && return 0; sleep $((i * 10)); done
  echo "could not download $path" >&2; exit 1; }
K=2.2.20
for p in org/jetbrains/kotlin/kotlin-compiler-embeddable/$K/kotlin-compiler-embeddable-$K \
         org/jetbrains/kotlin/kotlin-stdlib/$K/kotlin-stdlib-$K \
         org/jetbrains/kotlin/kotlin-script-runtime/$K/kotlin-script-runtime-$K \
         org/jetbrains/kotlin/kotlin-reflect/1.6.10/kotlin-reflect-1.6.10 \
         org/jetbrains/kotlin/kotlin-daemon-embeddable/$K/kotlin-daemon-embeddable-$K \
         org/jetbrains/intellij/deps/trove4j/1.0.20200330/trove4j-1.0.20200330 \
         org/jetbrains/kotlinx/kotlinx-coroutines-core-jvm/1.8.0/kotlinx-coroutines-core-jvm-1.8.0 \
         org/jetbrains/annotations/13.0/annotations-13.0 \
         junit/junit/4.13.2/junit-4.13.2 org/hamcrest/hamcrest-core/1.3/hamcrest-core-1.3 \
         org/apache/pdfbox/pdfbox/2.0.27/pdfbox-2.0.27 org/apache/pdfbox/fontbox/2.0.27/fontbox-2.0.27 \
         commons-logging/commons-logging/1.2/commons-logging-1.2 net/sf/kxml/kxml2/2.3.0/kxml2-2.3.0; do
  fetch "$p"
done
CP=$(ls "$LIB"/*.jar | tr '\n' ':')
rm -rf "$OUT" && mkdir -p "$GEN"
sed 's/com\.tom_roush\.pdfbox/org.apache.pdfbox/g' "$P/pdf/PdfTools.kt" > "$GEN/PdfTools.kt"
SRC=("$P/ai/SummaryParagraphs.kt" "$P/data/files/FileNames.kt" "$P/office/OoxmlZip.kt" "$P/office/HtmlPage.kt"
     "$P/office/XlsxToHtml.kt" "$P/office/TextToHtml.kt" "$P/office/DocxToHtml.kt" "$P/office/PptxToHtml.kt" "$GEN/PdfTools.kt" "$HERE"/stubs/*.kt)
TESTS=("$T/ai/SummaryParagraphsTest.kt" "$T/data/files/FileNamesTest.kt" "$T/office/XlsxColumnTest.kt"
       "$T/office/OoxmlPathTest.kt" "$T/office/TextToHtmlTest.kt" "$T/office/DocxFormatTest.kt" "$HERE"/checks/*.kt)
java -cp "$CP" org.jetbrains.kotlin.cli.jvm.K2JVMCompiler "${SRC[@]}" "${TESTS[@]}" \
  -d "$OUT/classes" -classpath "$CP" -jvm-target 17 -nowarn 2>&1 | grep -v "JAVA_TOOL\|Kotlin home" || true
cd "$OUT"
java -cp "$OUT/classes:$CP" org.junit.runner.JUnitCore \
  com.whats.web.scan.webscan.pdfreaderpdffileedit.ai.SummaryParagraphsTest \
  com.whats.web.scan.webscan.pdfreaderpdffileedit.data.files.FileNamesTest \
  com.whats.web.scan.webscan.pdfreaderpdffileedit.office.XlsxColumnTest \
  com.whats.web.scan.webscan.pdfreaderpdffileedit.office.OoxmlPathTest \
  com.whats.web.scan.webscan.pdfreaderpdffileedit.office.TextToHtmlTest \
  com.whats.web.scan.webscan.pdfreaderpdffileedit.office.DocxFormatTest \
  check.PdfToolsCheck 2>&1 | grep -v "JAVA_TOOL\|WARNING\|FileSystemFontProvider\|PDType1Font"
java -cp "$OUT/classes:$CP" check.OfficeRunKt "$ROOT/app/src/main/assets/samples" "$OUT/office" 2>&1 | grep -v JAVA_TOOL
echo "Office HTML written to $OUT/office"
