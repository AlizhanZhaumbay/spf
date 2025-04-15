package com.example.spf.requests;

import com.example.spf.dtos.Filters;

public record TextSearchRequest(String text, Filters filters) {
}
