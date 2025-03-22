package com.EverLoad.everload.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

@RestController
@RequestMapping("/api/youtube")
@CrossOrigin(origins = "http://localhost:4200")
public class YouTubeController {

    private final RestTemplate restTemplate;
    private final String API_KEY = "AIzaSyCVzVmbSB5YVeYzOfiUtw3Hx_J58nGytxI";

    public YouTubeController(RestTemplate restTemplate) {
        this.restTemplate = restTemplate;
    }

    @GetMapping("/search")
    public ResponseEntity<String> searchVideos(@RequestParam String query) {
        String url = "https://www.googleapis.com/youtube/v3/search?part=snippet&type=video&maxResults=10&q="
                + query + "&key=" + API_KEY;

        String response = restTemplate.getForObject(url, String.class);
        return ResponseEntity.ok(response);
    }
}