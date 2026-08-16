package com.seggellion.britannia_mod.util;

import org.w3c.dom.Document;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

public class NameLoader {
    private static final List<String> maleNames = new ArrayList<>();
        private static final List<String> femaleNames = new ArrayList<>();

      public static void loadNames(String resourcePath) {
        try (InputStream inputStream = NameLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (inputStream == null) {
                System.err.println("Failed to load resource: " + resourcePath);
                return;
            }

            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            DocumentBuilder builder = factory.newDocumentBuilder();
            Document document = builder.parse(inputStream);

            document.getDocumentElement().normalize();

            NodeList nameNodes = document.getElementsByTagName("namelist");
for (int i = 0; i < nameNodes.getLength(); i++) {
                String type = nameNodes.item(i).getAttributes().getNamedItem("type").getNodeValue();
                
                // Split purely by comma first
                String[] names = nameNodes.item(i).getTextContent().split(",");

                for (String rawName : names) {
                    // .trim() strips all leading/trailing newlines, tabs, and spaces!
                    String cleanName = rawName.trim(); 
                    
                    if (!cleanName.isEmpty()) {
                        if ("male".equalsIgnoreCase(type)) {
                            maleNames.add(cleanName);
                        } else if ("female".equalsIgnoreCase(type)) {
                            femaleNames.add(cleanName);
                        }
                    }
                }
            }

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public static String getRandomMaleName() {
        if (maleNames.isEmpty()) {
            return "Default Name";
        }
        return maleNames.get((int) (Math.random() * maleNames.size()));
    }

    public static String getRandomFemaleName() {
        if (femaleNames.isEmpty()) {
            return "Default Name";
        }
        return femaleNames.get((int) (Math.random() * femaleNames.size()));
    }

}
