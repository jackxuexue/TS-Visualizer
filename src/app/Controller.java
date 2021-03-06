package app;

import app.streamAnalyzer.StreamAnalyzer;
import app.streamAnalyzer.StreamParser;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.stage.Stage;
import model.Stream;
import view.Window;

import java.io.File;
import java.io.IOException;

import static app.Main.localization;
import static model.config.Config.errorTitle;


public class Controller {

    private app.XML XML;

    private Thread streamParserThread, streamAnalyzerThread, fileHandlerThread, visualizerThread;
    private Stage primaryStage;
    private FileHandler fileHandler;
    private Window view;
    private StreamParser streamParser;
    private StreamAnalyzer streamAnalyzer;

    private Stream stream;


    Controller(Stage primaryStage, Window view) throws Exception {
        initialize(primaryStage, view);
    }


    private void initialize(Stage primaryStage, Window view) throws Exception {

        this.primaryStage = primaryStage;
        this.view = view;
        this.streamParser = new StreamParser(null);
        this.streamAnalyzer = new StreamAnalyzer(null);
        this.stream = null;
        this.XML = null;

        try {
            this.fileHandler = new FileHandler();
        } catch (IOException error) {
            error.printStackTrace();
            this.view.showAlertBox(errorTitle, String.valueOf(error.getMessage()));
        }


        this.view.scene.setOnDragOver(dragEvent -> {
            Dragboard db = dragEvent.getDragboard();
            if (db.hasFiles()) {
                dragEvent.acceptTransferModes(TransferMode.COPY);
            } else {
                dragEvent.consume();
            }
        });

        this.view.selectFileButton.setOnAction(actionEvent -> {
            File file = this.view.openFileChooser.showOpenDialog(this.view.primaryStage);
            if (file != null) {
                openFile(file);
            }
        });

        this.view.scene.setOnDragDropped(dragEvent -> {
            Dragboard dragboard = dragEvent.getDragboard();
            if (dragboard.hasFiles()) {
                openFile(dragboard.getFiles().get(0));
            }
            dragEvent.setDropCompleted(dragboard.hasFiles());
            dragEvent.consume();
        });

        this.view.exitApp.setOnAction(actionEvent -> {
            System.exit(0);
        });


        this.view.openFile.setOnAction(actionEvent -> {
            File file = this.view.openFileChooser.showOpenDialog(this.view.primaryStage);
            if (file != null) {
                openFile(file);
            }
        });

        this.view.userGuide.setOnAction(actionEvent -> {
            this.view.showUserGuide();
        });

        this.view.about.setOnAction(actionEvent -> {
            this.view.showAbout();
        });

        this.view.settings.setOnAction(actionEvent -> {
            this.view.showSettings();
        });

        this.view.importXML.setOnAction(actionEvent -> {
                    File file = this.view.openFileChooser.showOpenDialog(this.view.primaryStage);
                    if (file != null) {
                        try {
                            this.view.createTask((Stream) XML.read(file));
                        } catch (Exception error) {
                            error.printStackTrace();
                            this.view.showAlertBox(errorTitle, String.valueOf(error.getMessage()));
                        }
                    }
                }
        );

        this.view.exportXML.setOnAction(actionEvent -> {
                    File file = this.view.saveFileChooser.showSaveDialog(this.view.primaryStage);
                    if (file != null) {
                        try {
                            this.XML.save(this.stream, file);
                        } catch (Exception error) {
                            error.printStackTrace();
                            this.view.showAlertBox(errorTitle, String.valueOf(error.getMessage()));
                        }
                    }
                }
        );

        this.view.exportTXT.setOnAction(actionEvent -> {
                    File file = this.view.saveFileChooser.showSaveDialog(this.view.primaryStage);
                    if (file != null) {
                        try {
                            Exporter.export(this.view.getTreeData(), file);
                        } catch (Exception error) {
                            error.printStackTrace();
                            this.view.showAlertBox(errorTitle, String.valueOf(error.getMessage()));
                        }
                    }
                }
        );

        this.view.setButton.setOnAction(event -> {
            this.view.miniStage.close();
            this.view = new Window(this.primaryStage);
            try {
                initialize(this.primaryStage, this.view);
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    void createTask(File file) {

        fileHandler.getTask().setOnSucceeded(fileHandlerEvent -> {

            streamParser.parseStream(fileHandler.getTask().getValue());

            view.progressWindow.activateProgressBar(streamParser.getTask());

            streamParser.getTask().setOnSucceeded(streamParserEvent -> {
                        try {
                            view.progressWindow.activatePinBar(this.fileHandler.getTask(),localization.getAnalyseText());

                            streamAnalyzer.analyzeStream(file, streamParser.getTask().getValue());
                            streamAnalyzer.getTask().setOnSucceeded( streamAnalyzerEvent -> {

                                        view.createTask(streamAnalyzer.getTask().getValue());

                                        visualizerThread = new Thread(view.getTask());
                                        visualizerThread.start(); //TODO refactor

                                        view.progressWindow.getDialogStage().close();
                                    }
                            );
                            streamAnalyzer.getTask().setOnFailed(streamAnalyzerEvent -> {
                                        view.progressWindow.getDialogStage().close();
                                        streamAnalyzer.getTask().getException().printStackTrace();
                                        view.showAlertBox(errorTitle, String.valueOf(streamAnalyzer.getTask().getException().getStackTrace()) + streamAnalyzer.getTask().getException().getMessage());
                                    }
                            );
                            streamAnalyzerThread = new Thread(streamAnalyzer.getTask());
                            streamAnalyzerThread.start();
                        } catch (IOException e) {
                            e.printStackTrace();
                            view.showAlertBox(errorTitle, String.valueOf(e.getMessage()));
                        }


                    }
            );

            streamParser.getTask().setOnFailed(workerStateEvent -> {
                        view.progressWindow.getDialogStage().close();
                        streamParser.getTask().getException().printStackTrace();
                        view.showAlertBox(errorTitle, String.valueOf(streamParser.getTask().getException().getStackTrace()) + streamParser.getTask().getException().getMessage());
                    }
            );

            streamParserThread = new Thread(streamParser.getTask());
            streamParserThread.start();
        });

        fileHandler.getTask().setOnFailed(workerStateEvent ->
                {
                    view.progressWindow.getDialogStage().close();
                    fileHandler.getTask().setOnFailed(event -> {
                        fileHandler.getTask().getException().printStackTrace();
                        view.showAlertBox(errorTitle, fileHandler.getTask().getException().getMessage());
                    });
                }
        );
    }


    private void openFile(File file) {


        try {
            fileHandler.createTask(file);
        } catch (IOException e) {
            e.printStackTrace();
            view.showAlertBox(errorTitle, e.getMessage());
        }

        view.progressWindow.activatePinBar(this.fileHandler.getTask(),localization.getPrepareText());

        createTask(file);

        fileHandlerThread = new Thread(this.fileHandler.getTask());
        fileHandlerThread.start();

        view.progressWindow.getDialogStage().setOnCloseRequest(windowEvent ->   {
            try {
                this.restart();
            } catch (IOException error) {
                error.printStackTrace();
                this.view.showAlertBox(errorTitle, String.valueOf(error.getMessage()));
            } catch (Exception e) {
                e.printStackTrace();
            }
        });
    }


    private void restart () throws Exception { //TODO not working

        if (fileHandlerThread != null) {
            fileHandler.getTask().cancel();
            fileHandlerThread.interrupt();
            fileHandler = null;
            fileHandlerThread = null;

            if (streamParserThread != null) {
                streamParser.getTask().cancel();
                streamParserThread.interrupt();
                streamParser = null;
                streamParserThread = null;

                if (streamAnalyzerThread != null) {
                    streamAnalyzer.getTask().cancel();
                    streamAnalyzerThread.interrupt();
                    streamAnalyzer = null;
                    streamAnalyzerThread = null;

                    System.gc();
                    streamParser = new StreamParser(null);
                    fileHandler = new FileHandler();
                    streamAnalyzer = new StreamAnalyzer(null);
                }
            }
        }
        initialize(primaryStage,view);
    }
}