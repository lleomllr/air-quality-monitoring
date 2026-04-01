package com.example.demo.service;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import org.springframework.stereotype.Service;

import com.example.demo.dto.GlobalTopKEntryDTO;

@Service
public class GlobalTopKService {

    private final AtomicReference<List<GlobalTopKEntryDTO>> latestTopK = new AtomicReference<>(Collections.emptyList());

    public void updateTopK(List<GlobalTopKEntryDTO> entries) {
        latestTopK.set(List.copyOf(entries));
    }

    public List<GlobalTopKEntryDTO> getLatestTopK(int limit) {
        List<GlobalTopKEntryDTO> snapshot = latestTopK.get();
        if (limit <= 0 || limit >= snapshot.size()) {
            return snapshot;
        }
        return snapshot.subList(0, limit);
    }
}
