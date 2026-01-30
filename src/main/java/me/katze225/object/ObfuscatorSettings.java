package me.katze225.object;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class ObfuscatorSettings {
    private String mainClass;
    private int namesLength;

    // files
    private String inputPath;
    private String outputPath;

    // transformers
    private boolean enabledNumbers;
    private boolean enabledString;
    private boolean enabledBoolean;
    private boolean enabledFlow;
    private boolean enabledDispatcher;
    private boolean enabledShuffle;
    private boolean enabledZipComment;
    private String zipCommentText;
}
