package uz.kapitalbank.umida.component.loginbg;

import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.Tag;
import com.vaadin.flow.component.dependency.JsModule;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;

/**
 * Decorative full-screen background of the login view ("Dune" isometric scene with mouse parallax).
 * <p>
 * The scene markup is kept in {@code login-background-scene.html} next to this class, its images in
 * {@code META-INF/resources/public/images/login}, the styles in the theme ({@code view/login-view.css}).
 * The component is fixed under the page content, so the components placed above it must have
 * {@code position: relative; z-index: 1}.
 */
@Tag("umida-login-bg")
@JsModule("./src/component/loginbg/umida-login-bg.js")
public class LoginBackground extends Component {

    private static final String SCENE_RESOURCE = "login-background-scene.html";

    private static final String SCENE_HTML = loadScene();

    public LoginBackground() {
        getElement().getClassList().add("umida-login-bg");
        getElement().setAttribute("aria-hidden", "true");
        getElement().setProperty("innerHTML", SCENE_HTML);
    }

    private static String loadScene() {
        try (InputStream is = LoginBackground.class.getResourceAsStream(SCENE_RESOURCE)) {
            if (is == null) {
                throw new IllegalStateException("Login background scene not found: " + SCENE_RESOURCE);
            }
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read login background scene", e);
        }
    }
}
