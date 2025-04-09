package com.example.spf.dtos;

import java.util.Collections;
import java.util.List;

public record Filters(List<String> marketplaces){


    public static Filters emptyValue(){
        return new Filters(Collections.emptyList());
    }
}
