package com.example.perflab.service;
import java.util.Map;
import org.springframework.stereotype.Service;
@Service
public class LabWork {
    public Map<String,Object> cpu(int iterations) {
        double result=0;
        for(int i=1;i<=iterations;i++) result += Math.sqrt(i)*Math.sin(i);
        return Map.of("iterations",iterations,"checksum",result);
    }
    public Map<String,Object> alloc(int kb) {
        byte[] data=new byte[kb*1024];
        long checksum=0;
        for(int i=0;i<data.length;i++) {
            data[i]=(byte)(i%127);
            checksum+=data[i];
        }
        return Map.of("allocatedBytes",data.length,"checksum",checksum);
    }
}
