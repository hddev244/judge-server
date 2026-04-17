package com.judge.judge;

import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

@Component
public class OutputComparator {

    public boolean compare(String actualOutput, String expectedFilePath) {
        try {
            String expected = Files.readString(Path.of(expectedFilePath));
            return normalize(actualOutput).equals(normalize(expected));
        } catch (IOException e) {
            return false;
        }
    }

    private List<String> normalize(String text) {
        return text.lines()
                .map(String::stripTrailing)
                .dropWhile(String::isBlank)
                .collect(java.util.stream.Collectors.collectingAndThen(
                        java.util.stream.Collectors.toList(),
                        list -> {
                            int end = list.size();
                            while (end > 0 && list.get(end - 1).isBlank()) end--;
                            return list.subList(0, end);
                        }
                ));
    }
}
