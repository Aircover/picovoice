/*
    Copyright 2018-2020 Picovoice Inc.

    You may not use this file except in compliance with the license. A copy of the license is
    located in the "LICENSE" file accompanying this source.

    Unless required by applicable law or agreed to in writing, software distributed under the
    License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either
    express or implied. See the License for the specific language governing permissions and
    limitations under the License.
*/

package ai.picovoice.picovoice;

import ai.picovoice.rhino.RhinoInference;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.stream.Stream;
import javax.sound.sampled.AudioInputStream;
import javax.sound.sampled.AudioSystem;
import javax.sound.sampled.UnsupportedAudioFileException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PicovoiceTest {

    private Picovoice picovoice;
    private final String accessKey = System.getProperty("pvTestingAccessKey");
    private final static String environmentName = getEnvironmentName();

    private boolean isWakeWordDetected = false;
    private final PicovoiceWakeWordCallback wakeWordCallback = new PicovoiceWakeWordCallback() {
        @Override
        public void invoke() {
            isWakeWordDetected = true;
        }
    };

    private RhinoInference inferenceResult;
    private final PicovoiceInferenceCallback inferenceCallback = new PicovoiceInferenceCallback() {
        @Override
        public void invoke(RhinoInference inference) {
            inferenceResult = inference;
        }
    };

    private static String appendLanguage(String s, String language) {
        if (language == "en")
            return s;
        return s + "_" + language;
    }

    private static String getTestKeywordPath(String language, String keyword) {
        return Paths.get(System.getProperty("user.dir"))
            .resolve("../../resources/porcupine/resources")
            .resolve(appendLanguage("keyword_files", language))
            .resolve(environmentName)
            .resolve(keyword + "_" + environmentName + ".ppn")
            .toString();
    }

    private static String getTestPorcupineModelPath(String language) {
        return Paths.get(System.getProperty("user.dir"))
            .resolve("../../resources/porcupine/lib/common")
            .resolve(appendLanguage("porcupine_params", language)+".pv")
            .toString();
    }

    private static String getTestContextPath(String language, String context) {
        return Paths.get(System.getProperty("user.dir"))
            .resolve("../../resources/rhino/resources")
            .resolve(appendLanguage("contexts", language))
            .resolve(environmentName)
            .resolve(context + "_" + environmentName + ".rhn")
            .toString();
    }

    private static String getTestRhinoModelPath(String language) {
        return Paths.get(System.getProperty("user.dir"))
            .resolve("../../resources/rhino/lib/common")
            .resolve(appendLanguage("rhino_params", language)+".pv")
            .toString();
    }  

    private static String getTestAudioFilePath(String audioFileName) {
        return Paths.get(System.getProperty("user.dir"))
            .resolve("../../resources/audio_samples")
            .resolve(audioFileName)
            .toString();
    }

    @AfterEach
    void tearDown() {
        picovoice.delete();
    }

    @Test
    void getFrameLength() throws PicovoiceException {
        final String language = "en";
        picovoice = new Picovoice.Builder()
                .setAccessKey(accessKey)
                .setPorcupineModelPath(getTestPorcupineModelPath(language))
                .setKeywordPath(getTestKeywordPath(language, "picovoice"))
                .setWakeWordCallback(wakeWordCallback)
                .setRhinoModelPath(getTestRhinoModelPath(language))
                .setContextPath(getTestContextPath(language, "coffee_maker"))
                .setInferenceCallback(inferenceCallback)
                .build();
        assertTrue(picovoice.getFrameLength() > 0);
    }

    @Test
    void getSampleRate() throws PicovoiceException {
        final String language = "en";
        picovoice = new Picovoice.Builder()
                .setAccessKey(accessKey)
                .setPorcupineModelPath(getTestPorcupineModelPath(language))
                .setKeywordPath(getTestKeywordPath(language, "picovoice"))
                .setWakeWordCallback(wakeWordCallback)
                .setRhinoModelPath(getTestRhinoModelPath(language))
                .setContextPath(getTestContextPath(language, "coffee_maker"))
                .setInferenceCallback(inferenceCallback)
                .build();     
        assertTrue(picovoice.getSampleRate() > 0);
    }

    void runTestCase(String audioFileName, String expectedIntent, Map<String, String> expectedSlots) throws PicovoiceException, IOException, UnsupportedAudioFileException {
        isWakeWordDetected = false;
        inferenceResult = null;
        
        int frameLen = picovoice.getFrameLength();
        File testAudioPath = new File(getTestAudioFilePath(audioFileName));

        AudioInputStream audioInputStream = AudioSystem.getAudioInputStream(testAudioPath);
        assertEquals(audioInputStream.getFormat().getFrameRate(), 16000);

        int byteDepth = audioInputStream.getFormat().getFrameSize();
        byte[] pcm = new byte[frameLen * byteDepth];
        short[] picovoiceFrame = new short[frameLen];
        int numBytesRead;
        while ((numBytesRead = audioInputStream.read(pcm)) != -1) {

            if (numBytesRead / byteDepth == frameLen) {
                ByteBuffer.wrap(pcm).order(ByteOrder.LITTLE_ENDIAN).asShortBuffer().get(picovoiceFrame);
                picovoice.process(picovoiceFrame);
            }
        }

        assertTrue(isWakeWordDetected);
        assertEquals(inferenceResult.getIntent(), expectedIntent);
        assertEquals(inferenceResult.getSlots(), expectedSlots);
    }

    @ParameterizedTest(name = "testIntentDetection for ''{0}''")
    @MethodSource("intentDetectionProvider")
    void testIntentDetection(String language, String keyword, String context, String audioFileName, String expectedIntent, Map<String, String> expectedSlots) throws PicovoiceException, IOException, UnsupportedAudioFileException {
        picovoice = new Picovoice.Builder()
                .setAccessKey(accessKey)
                .setPorcupineModelPath(getTestPorcupineModelPath(language))
                .setKeywordPath(getTestKeywordPath(language, keyword))
                .setWakeWordCallback(wakeWordCallback)
                .setRhinoModelPath(getTestRhinoModelPath(language))
                .setContextPath(getTestContextPath(language, context))
                .setInferenceCallback(inferenceCallback)
                .build();

        runTestCase(audioFileName, expectedIntent, expectedSlots);
        runTestCase(audioFileName, expectedIntent, expectedSlots);
    }

    private static Stream<Arguments> intentDetectionProvider() {
        return Stream.of(
                Arguments.of("en", "picovoice", "coffee_maker", "picovoice-coffee.wav", "orderBeverage", new HashMap<String, String>() {{
                    put("size", "large");
                    put("beverage", "coffee");
                }}),
                Arguments.of("de", "heuschrecke", "beleuchtung", "heuschrecke-beleuchtung_de.wav", "changeState", new HashMap<>() {{
                    put("state", "aus");
                }}),
                Arguments.of("es", "manzana", "iluminación_inteligente", "manzana-luz_es.wav", "changeColor", new HashMap<>() {{
                    put("location", "habitación");
                    put("color", "rosado");
                }}),
                Arguments.of("fr", "mon chouchou", "éclairage_intelligent", "mon-intelligent_fr.wav", "changeColor", new HashMap<>() {{
                    put("color", "violet");
                }}),
                Arguments.of("it", "cameriere", "illuminazione", "cameriere-luce_it.wav", "spegnereLuce", new HashMap<>() {{
                    put("luogo", "bagno");
                }}),
                Arguments.of("ja", "ninja", "sumāto_shōmei", "ninja-sumāto-shōmei_ja.wav", "色変更", new HashMap<>() {{
                    put("色", "オレンジ");
                }}),
                Arguments.of("ko", "koppulso", "seumateu_jomyeong", "koppulso-seumateu-jomyeong_ko.wav", "changeColor", new HashMap<>() {{
                    put("color", "파란색");
                }}),
                Arguments.of("pt", "abacaxi", "luz_inteligente", "abaxi-luz_pt.wav", "ligueLuz", new HashMap<String, String>() {{
                    put("lugar", "cozinha");
                }})
        );
    }

    private static String getEnvironmentName() throws RuntimeException {
        String os = System.getProperty("os.name", "generic").toLowerCase(Locale.ENGLISH);
        if (os.contains("mac") || os.contains("darwin")) {
            return "mac";
        } else if (os.contains("win")) {
            return "windows";
        } else if (os.contains("linux")) {
            String arch = System.getProperty("os.arch");
            if (arch.equals("arm") || arch.equals("aarch64")) {
                String cpuPart = getCpuPart();
                switch (cpuPart) {
                    case "0xc07":
                    case "0xd03":
                    case "0xd08":
                        return "raspberry-pi";
                    case "0xd07":
                        return "jetson";
                    case "0xc08":
                        return "beaglebone";
                    default:
                        throw new RuntimeException(String.format("Execution environment not supported. " +
                                "Picovoice Java does not support CPU Part (%s).", cpuPart));
                }
            }
            return "linux";
        } else {
            throw new RuntimeException("Execution environment not supported. " +
                    "Picovoice Java is supported on MacOS, Linux and Windows");
        }
    }

    private static String getCpuPart() throws RuntimeException {
        try {
            return Files.lines(Paths.get("/proc/cpuinfo"))
                    .filter(line -> line.startsWith("CPU part"))
                    .map(line -> line.substring(line.lastIndexOf(" ") + 1))
                    .findFirst()
                    .orElse("");
        } catch (IOException e) {
            throw new RuntimeException("Picovoice failed to get get CPU information.");
        }
    }
}
