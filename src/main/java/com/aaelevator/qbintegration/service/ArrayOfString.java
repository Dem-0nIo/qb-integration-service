package com.aaelevator.qbintegration.service;

import jakarta.xml.bind.annotation.*;
import java.util.List;
import java.util.ArrayList;

@XmlAccessorType(XmlAccessType.FIELD)
public class ArrayOfString {

    @XmlElement(name = "string", namespace = "http://developer.intuit.com/")
    private List<String> string = new ArrayList<>();

    public ArrayOfString() {}

    public ArrayOfString(String... values) {
        for (String v : values) {
            string.add(v);
        }
    }

    public List<String> getString() { return string; }
}