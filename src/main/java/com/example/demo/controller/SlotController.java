package com.example.demo.controller;

import com.example.demo.service.SlotDetectionService;
import com.example.demo.service.SlotDetectionService.TimeInterval;
import lombok.AllArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
import java.util.List;

@RestController
@RequestMapping("/api/slots")
@AllArgsConstructor
public class SlotController {

    private final SlotDetectionService slotDetectionService;

    @GetMapping("/detect")
    public ResponseEntity<List<TimeInterval>> getFreeSlots(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
            @RequestParam(defaultValue = "60") int duration) {
        
        List<TimeInterval> freeSlots = slotDetectionService.detectFreeSlots(date, duration);
        return ResponseEntity.ok(freeSlots);
    }
}
