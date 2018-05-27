package de.dynamicfiles.projects.maven.distributionbundleplugin.example.simplejavafxwithspringbootsetup;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.EnableAutoConfiguration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseBody;

import javafx.application.Application;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.stage.Stage;

@Controller
@EnableAutoConfiguration
public class MainEntrypoint extends Application {

    @RequestMapping("/")
    @ResponseBody
    String home() {
        return "Hello World!";
    }

    public static void main(String[] args) {
        // some notes: this is not ideal and not optimized ;)

        // first start SpringBoot Application
        SpringApplication.run(MainEntrypoint.class, args);
        // Then launch the JavaFX-thread (as it is blocking)
        Application.launch(args);
    }

    @Override
    public void start(Stage primaryStage) throws Exception {
        primaryStage.setScene(new Scene(new Label("Hello World!")));
        primaryStage.show();
    }
}
