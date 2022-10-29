package com.boots;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;


@SpringBootApplication
//@ComponentScan
public class Application {
    public static void main(String[] args) {

        SpringApplication.run(Application.class, args);

        //AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext(MapDBSet.class);
        //MapDBSet mapDB = context.getBean(MapDBSet.class);
        //mapDB.getMap03().put("something", "here");
        //System.out.println(mapDB.getMap03().get("something"));
    }
}
