package ph.edu.dlsu;

import javafx.animation.FadeTransition;
import javafx.animation.TranslateTransition;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.beans.property.DoubleProperty;
import javafx.beans.property.ReadOnlyDoubleProperty;
import javafx.beans.property.SimpleDoubleProperty;
import javafx.concurrent.Task;
import javafx.event.ActionEvent;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.MouseEvent;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.FlowPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Arc;
import javafx.scene.shape.ArcType;
import javafx.scene.shape.Rectangle;
import javafx.scene.text.Text;
import javafx.stage.Stage;
import javafx.util.Duration;

import java.net.MalformedURLException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;

public class Main extends Application {

    // List of URL strings
    private final List<String> imageFiles = new ArrayList<>();

    // The current index into the imageFile
    private int currentIndex = -1;

    // Enumeration of next and previous button directions
    public enum ButtonMove {
        NEXT, PREV
    }

    ;

    //
    private Group caption;

    // Current image view display
    private ImageView currentImageView;

    // Loading progress indicator
    private ProgressIndicator progressIndicator;

    // mutex */
    private AtomicBoolean loading = new AtomicBoolean();


    public static void main(String[] args) {
        launch(args);
    }


    @Override
    public void start(Stage primaryStage) {
        primaryStage.setTitle("Photo Viewer");
        Group root = new Group();
        Scene scene = new Scene(root, 551, 400, Color.BLACK);
        scene.getStylesheets()
                .add(getClass()
                        .getResource("photo-viewer.css")
                        .toExternalForm());
        scene.getStylesheets()
                .add(getClass().getResource("ticker-photo-viewer.css")
                        .toExternalForm()
                );
        primaryStage.setScene(scene);
        // set up the current image view area
        currentImageView = createImageView(scene.widthProperty());
        // set up drag & drop file abilities
        setupDragNDrop(scene);
        // create button panel controls (left & right arrows)
        Group buttonGroup = createButtonPanel(scene);
        // create a progress indicator
        progressIndicator = createProgressIndicator(scene);
        caption = createTickerControl(primaryStage, 85); // constant adjusts right padding
        root.getChildren().addAll(currentImageView,
                buttonGroup,
                caption,
                progressIndicator);
        primaryStage.show();
    }

    /*
* A factory function returning an ImageView instance to
* preserve the aspect ratio and bind the instance to the width
* of the scene for resizing the image.
*
* @param widthProperty is the Scene's read only width property.
* @return ImageView newly created image view for current display.
*/
    private ImageView createImageView(ReadOnlyDoubleProperty widthProperty) {
// maintain aspect ratio
        ImageView imageView = new ImageView();
// set aspect ratio
        imageView.setPreserveRatio(true);
// resize based on the scene
        imageView.fitWidthProperty().bind(widthProperty);
        return imageView;
    }


    /*
    * Sets up the drag and drop capability for files and URLs to be
    * dragged and dropped onto the scene. This will load the image into
    * the current image view area.
    * @param scene The primary application scene.
    */
    private void setupDragNDrop(Scene scene) {
        // Dragging over surface
        scene.setOnDragOver((DragEvent event) -> {
            Dragboard db = event.getDragboard();
            if (db.hasFiles()
                    || (db.hasUrl()
                    && isValidImageFile(db.getUrl()))) {
                event.acceptTransferModes(TransferMode.LINK);
            } else {
                event.consume();
            }
        });

        // Dropping over surface
        scene.setOnDragDropped((DragEvent event) -> {
            Dragboard db = event.getDragboard();

            // image from the local file system.
            if (db.hasFiles() && !db.hasUrl()) {
                db.getFiles()
                        .stream()
                        .forEach(file -> {
                            try {
                                addImage(file.toURI().toURL().toString());
                            } catch (MalformedURLException ex) {
                                ex.printStackTrace();
                            }
                        });
            } else {
                // image from some host
                addImage(db.getUrl());
            }
            if (currentIndex > -1) {
                loadImage(imageFiles.get(currentIndex));
            }
            event.setDropCompleted(true);
            event.consume();
        });
    }


    /*
* Returns a custom created button panel
* containing left and right buttons to
* see previous and next images.
* @param scene The main application scene
* @return Group A custom button panel with
* previous and next buttons
*/
    private Group createButtonPanel(Scene scene) {
        // create button panel
        Group buttonGroup = new Group();
        Rectangle buttonArea = new Rectangle(0, 0, 65, 30);
        buttonArea.getStyleClass().add("button-panel");
        buttonGroup.getChildren().add(buttonArea);
        // left arrow button
        Arc leftButton = new Arc(12, 16, 15, 15, -30, 60);
        leftButton.setType(ArcType.ROUND);
        leftButton.getStyleClass().add("left-arrow");

        // return to previous image
        leftButton.addEventHandler(MouseEvent.MOUSE_PRESSED,
                (mouseEvent) -> {
                    // System.out.println("busy loading? " + loading.get());
                    // if no previous image or currently loading.
                    if (currentIndex == 0 || loading.get()) return;
                    int indx = gotoImageIndex(ButtonMove.PREV);
                    if (indx > -1) {
                        loadImage(imageFiles.get(indx));
                    }
                });

        // right arrow button
        Arc rightButton = new Arc(12, 16, 15, 15, 180 - 30, 60);
        rightButton.setType(ArcType.ROUND);
        rightButton.getStyleClass().add("right-arrow");
        // advance to next image
        rightButton.addEventHandler(MouseEvent.MOUSE_PRESSED,
                (mouseEvent) -> {
                    // System.out.println("busy loading? " + loading.get());
                    // if no next image or currently loading.
                    if (currentIndex == imageFiles.size() - 1
                            || loading.get()) return;
                    int indx = gotoImageIndex(ButtonMove.NEXT);
                    if (indx > -1) {
                        loadImage(imageFiles.get(indx));
                    }
                });

        // add buttons to button group
        buttonGroup.getChildren().addAll(leftButton, rightButton);
        // move button group when scene is resized
        buttonGroup.translateXProperty()
                .bind(scene.widthProperty()
                        .subtract(buttonArea.getWidth() + 6));
        buttonGroup.translateYProperty()
                .bind(scene.heightProperty()
                        .subtract(buttonArea.getHeight() + 6));


        // Fade in button controls
        scene.setOnMouseEntered((MouseEvent me) -> {
            FadeTransition fadeButtons =
                    new FadeTransition(Duration.millis(500), buttonGroup);
            fadeButtons.setFromValue(0.0);
            fadeButtons.setToValue(1.0);
            fadeButtons.play();
        });

        // Fade out button controls
        scene.setOnMouseExited((MouseEvent me) -> {
            FadeTransition fadeButtons =
                    new FadeTransition(Duration.millis(500), buttonGroup);
            fadeButtons.setFromValue(1);
            fadeButtons.setToValue(0);
            fadeButtons.play();
        });

        return buttonGroup;
    }


    /*
* Create a progress indicator control to be centered.
* @param scene The primary application scene.
* @return ProgressIndicator a new progress indicator centered.
*/
    private ProgressIndicator createProgressIndicator(Scene scene) {
        ProgressIndicator progress = new ProgressIndicator(0);
        progress.setVisible(false);
        progress.layoutXProperty()
                .bind(scene.widthProperty()
                        .subtract(progress.widthProperty())
                        .divide(2));
        progress.layoutYProperty()
                .bind(scene.heightProperty()
                        .subtract(progress.heightProperty())
                        .divide(2));
        return progress;
    }


    /*
* Returns true if URL's file extensions match jpg, jpeg, png and gif.
* @param url standard URL path to image file.
* @return boolean returns true if URL's extension matches jpg, jpeg,
* png and gif.
*/
    private boolean isValidImageFile(String url) {
        List<String> imgTypes = Arrays.asList(".jpg", ".jpeg", ".png", ".gif", ".bmp");
        return imgTypes.stream()
                .anyMatch(url::endsWith);
    }

    /**
     * Adds the URL string representation of the path to the image file.
     * Based on a URL the method will check if it matches supported
     * image format.
     *
     * @param url string representation of the path to the image file.
     */
    private void addImage(String url) {
        if (isValidImageFile(url)) {
            currentIndex += 1;
            imageFiles.add(currentIndex, url);
        }
    }

    /**
     * Returns the next index in the list of files to go to next.
     *
     * @param direction PREV and NEXT to move backward or forward
     *                  in the list of pictures.
     * @return int the index to the previous or next picture to be shown.
     */
    private int gotoImageIndex(ButtonMove direction) {
        int size = imageFiles.size();
        if (size == 0) {
            currentIndex = -1;
        } else if (direction == ButtonMove.NEXT
                && size > 1
                && currentIndex < size - 1) {
            currentIndex += 1;
        } else if (direction == ButtonMove.PREV
                && size > 1
                && currentIndex > 0) {
            currentIndex -= 1;
        }
        return currentIndex;
    }

    /*
    * Returns a worker task (Task) which will off-load the image
    * on a separate thread when finished; the current image will
    * be displayed on the JavaFX application thread.
    * @param url string representation of the path to the image file.
    * @return
    */
    private Task createWorker(final String url) {
        return new Task() {
            @Override
            protected Object call() throws Exception {
                // on the worker thread...
                Image image = new Image(url, false);
                Platform.runLater(() -> {
                    // on the JavaFX Application Thread....
                    System.out.println("done loading image " + url);
                    currentImageView.setImage(image);
                    progressIndicator.setVisible(false);
                    loading.set(false); // free lock
                });
                return true;
            }
        };
    }


    /*
* This method does the following loads an image,
* updates a progress bar and spawns a new thread.
* If another process is already loading
* the method will return without loading.
* @param url string representation of the path to the image file.
*/
    private void loadImage(String url) {
        if (!loading.getAndSet(true)) {
            Task loadImage = createWorker(url);
            progressIndicator.setVisible(true);
            progressIndicator.progressProperty().unbind();
            progressIndicator.progressProperty()
                    .bind(loadImage.progressProperty());
            new Thread(loadImage).start();
        }
    }


    private Group createTickerControl(Stage stage, double rightPadding) {
        Scene scene = stage.getScene();

        // create ticker area
        Group tickerArea = new Group();
        Rectangle tickerRect = new Rectangle(scene.getWidth(), 38); // constant height
        tickerRect.getStyleClass().add("ticker-border");
        tickerRect.setFill(Color.BLACK);
        tickerRect.setVisible(false);

        Rectangle clipRegion = new Rectangle(scene.getWidth(), 38);
        clipRegion.getStyleClass().add("ticker-clip-region");
        tickerArea.setClip(clipRegion);

        // Resize the ticker area when the window is resized
        tickerArea.setTranslateX(6);
        tickerArea.translateYProperty()
                .bind(scene.heightProperty()
                        .subtract(tickerRect.getHeight() + 6));
        tickerRect.widthProperty()
                .bind(scene.widthProperty()
                        .subtract(rightPadding));
        clipRegion.widthProperty()
                .bind(scene.widthProperty()
                        .subtract(rightPadding));
        tickerArea.getChildren().add(tickerRect);

        // News feed container
        FlowPane tickerContent = new FlowPane();

        Text news = new Text();
        news.setText("JavaFX 8.0 News! | 85 and sunny | :)");
        news.setFill(Color.WHITE);
        tickerContent.getChildren().add(news);
        DoubleProperty centerContentY = new SimpleDoubleProperty();
        centerContentY.bind(
                clipRegion.heightProperty()
                        .divide(2)
                        .subtract(tickerContent.heightProperty()
                                .divide(2)));
        tickerContent.translateYProperty().bind(centerContentY);
        tickerArea.getChildren().add(tickerContent);

        // scroll news feed
        TranslateTransition tickerScroller = new TranslateTransition();
        tickerScroller.setNode(tickerContent);
        tickerScroller.setDuration(
                Duration.millis(scene.getWidth() * 40));
        tickerScroller.fromXProperty()
                .bind(scene.widthProperty());
        tickerScroller.toXProperty()
                .bind(tickerContent.widthProperty()
                        .negate());

        // when ticker has finished, reset and replay ticker animation
        tickerScroller.setOnFinished((ActionEvent ae) -> {
            tickerScroller.stop();
            tickerScroller.setDuration(
                    Duration.millis(scene.getWidth() * 40));
            tickerScroller.playFromStart();
        });

        // start ticker after nodes are shown
        stage.setOnShown(windowEvent -> tickerScroller.play());
        return tickerArea;
    }
}