package com.example.perflab;
import com.example.perflab.generator.DataGenerator.Options;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
class DataGeneratorTest {
    @Test void requiresBoundedCount() {
        for(String[] args:new String[][] {
            {
            }
            , {
                "--count=0"
            }
            , {
                "--count=1000001"
            }
            , {
                "--count=1","--batchSize=10001"
            }
            , {
                "--count=1","--truncate=yes"
            }
            , {
                "--count=1","--sql=drop"
            }
        }
        ) assertThrows(IllegalArgumentException.class,()->Options.parse(args));
    }
    @Test void parsesExplicitOptions() {
        var result=Options.parse(new String[] {
            "--count=100","--seed=7","--batchSize=10","--truncate=true"
        }
        );
        assertEquals(100,result.count());
        assertEquals(10,result.batchSize());
        assertEquals(7,result.seed());
        assertTrue(result.truncate());
    }
}
