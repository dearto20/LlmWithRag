package com.example.llmwithrag.kg;

import java.util.Map;

public interface IElement {
    String getId();
    String getContentId();
    Map<String, String> getDescription();
    String getName();
    String getType();
}
