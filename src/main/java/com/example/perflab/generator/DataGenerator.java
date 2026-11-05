package com.example.perflab.generator;
import java.sql.*;
import java.time.Instant;
import java.util.*;
import java.math.BigDecimal;
public class DataGenerator {
    public record Options(int count,int batchSize,long seed,boolean truncate) {
        public static Options parse(String[] args) {
            Map<String,String> p=new HashMap<>();
            for(String arg:args) {
                String[] parts=arg.replaceFirst("^--", "").split("=",2);
                if(parts.length!=2 || !Set.of("count","batchSize","seed","truncate","maxCount").contains(parts[0]) || p.put(parts[0],parts[1])!=null) throw new IllegalArgumentException("无效或重复参数: "+arg);
            }
            int count=Integer.parseInt(p.getOrDefault("count","0")), batch=Integer.parseInt(p.getOrDefault("batchSize","1000")), max=Integer.parseInt(p.getOrDefault("maxCount","1000000"));
            String truncate=p.getOrDefault("truncate","false");
            if(count<1 || count>max || max<1 || batch<1 || batch>10000 || !Set.of("true","false").contains(truncate)) throw new IllegalArgumentException("count 必填且为 1..maxCount；batchSize 为 1..10000；truncate 为 true/false");
            return new Options(count,batch,Long.parseLong(p.getOrDefault("seed","42")),Boolean.parseBoolean(truncate));
        }
    }
    public static void main(String[] args) {
        long started=System.nanoTime();
        int inserted=0;
        int target=0;
        try {
            Options o=Options.parse(args);
            target=o.count();
            // 固定本地实验库，不接受 JDBC URL 或远程地址。
            try(Connection c=DriverManager.getConnection("jdbc:postgresql://127.0.0.1:5432/perflab","perflab",System.getenv().getOrDefault("DB_PASSWORD",""))) {
                c.setAutoCommit(false);
                try {
                    try(Statement s=c.createStatement()) {
                        s.execute("SET LOCAL lock_timeout='5s'");
                        s.execute("LOCK TABLE products, orders IN ACCESS EXCLUSIVE MODE");
                        if(o.truncate()) s.execute("TRUNCATE orders, products RESTART IDENTITY");
                    }
                    long base;
                    try(var s=c.createStatement();var rs=s.executeQuery("SELECT COALESCE(MAX(id),0) FROM products")) {
                        rs.next();
                        base=rs.getLong(1);
                    }
                    Random random=new Random(o.seed());
                    String[] categories= {
                        "Books","Office","Electronics","Home","Sports"
                    };
                    try(var ps=c.prepareStatement("INSERT INTO products(id,sku,name,category,price,created_at) VALUES (?,?,?,?,?,?)")) {
                        for(int i=1;i<=o.count();i++) {
                            long id=base+i;
                            ps.setLong(1,id);
                            ps.setString(2,"LAB-"+id);
                            ps.setString(3,"Product-"+id+"-"+random.nextInt(10000));
                            ps.setString(4,categories[random.nextInt(categories.length)]);
                            ps.setBigDecimal(5,BigDecimal.valueOf(100+random.nextInt(99900),2));
                            ps.setTimestamp(6,Timestamp.from(Instant.parse("2024-01-01T00:00:00Z").plusSeconds(random.nextInt(31536000))));
                            ps.addBatch();
                            if(i%o.batchSize()==0 || i==o.count()) ps.executeBatch();
                        }
                    }
                    try(var s=c.createStatement()) {
                        s.execute("SELECT setval(pg_get_serial_sequence('products','id'),(SELECT MAX(id) FROM products))");
                    }
                    c.commit();
                    inserted=o.count();
                } catch(Exception e) {
                    c.rollback();
                    throw e;
                }
            }
            double seconds=(System.nanoTime()-started)/1e9;
            System.out.printf("target=%d inserted=%d seconds=%.3f rows/s=%.1f%n",target,inserted,seconds,inserted/seconds);
        } catch(Exception e) {
            System.err.printf("target=%d inserted=%d failure=%s%n",target,inserted,e.getMessage());
            System.exit(1);
        }
    }
}
