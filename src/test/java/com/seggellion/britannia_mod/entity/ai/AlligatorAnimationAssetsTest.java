package com.seggellion.britannia_mod.entity.ai;

import com.google.gson.JsonParser;
import org.junit.jupiter.api.Test;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

class AlligatorAnimationAssetsTest {
    @Test void everyRequestedClipExistsInTheShippedAlligatorAsset() throws Exception {
        Path project = Path.of(System.getProperty("britannia.projectDir", "."));
        var clips = JsonParser.parseString(Files.readString(project.resolve(
                "src/main/resources/assets/britannia_mod/animations/alligator.animation.json")))
                .getAsJsonObject().getAsJsonObject("animations").keySet();
        String policy = Files.readString(project.resolve(
                "src/main/java/com/seggellion/britannia_mod/entity/AlligatorEntity.java"));
        var requests = Pattern.compile("then(?:Loop|Play)\\(\"([^\"]+)\"\\)").matcher(policy);
        int count = 0;
        while (requests.find()) {
            assertTrue(clips.contains(requests.group(1)), "Missing Alligator clip: " + requests.group(1));
            count++;
        }
        assertTrue(count > 0, "No Alligator asset request inspected");
    }
}
