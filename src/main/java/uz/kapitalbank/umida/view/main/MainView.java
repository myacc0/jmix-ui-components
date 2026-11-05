package uz.kapitalbank.umida.view.main;

import uz.kapitalbank.umida.entity.User;
import uz.kapitalbank.umida.service.user.UserProfileService;
import uz.kapitalbank.umida.service.user.UserAvatars;
import com.google.common.base.Strings;
import com.vaadin.flow.component.Component;
import com.vaadin.flow.component.avatar.Avatar;
import com.vaadin.flow.component.avatar.AvatarVariant;
import com.vaadin.flow.component.dependency.JsModule;
import com.vaadin.flow.component.html.Div;
import com.vaadin.flow.dom.Style;
import com.vaadin.flow.component.html.Span;
import com.vaadin.flow.router.Route;
import io.jmix.core.Messages;
import io.jmix.core.usersubstitution.CurrentUserSubstitution;
import io.jmix.flowui.UiComponents;
import io.jmix.flowui.app.main.StandardMainView;
import com.vaadin.flow.theme.lumo.LumoUtility;
import io.jmix.core.security.CurrentAuthentication;
import io.jmix.flowui.component.main.JmixListMenu;
import io.jmix.flowui.kit.component.main.ListMenu;
import io.jmix.flowui.view.Install;
import io.jmix.flowui.view.Subscribe;
import io.jmix.flowui.view.ViewComponent;
import io.jmix.flowui.view.ViewController;
import io.jmix.flowui.view.ViewDescriptor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.event.EventListener;
import uz.kapitalbank.umida.service.report.ReportAccessService;
import uz.kapitalbank.umida.view.report.access.ReportAccessUiEventBridge;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.List;

@Route("")
@JsModule("./src/icons/umida-icons.js")
@ViewController(id = "umida_MainView")
@ViewDescriptor(path = "main-view.xml")
public class MainView extends StandardMainView {

    /** The menu item of the access management of the reports, and the menu holding it, see menu.xml. */
    private static final String REPORT_ACCESS_ITEM = "reportAccessManagement";
    private static final String REPORTS_MENU = "reports_custom";

    @Autowired
    private Messages messages;
    @Autowired
    private UiComponents uiComponents;
    @Autowired
    private CurrentUserSubstitution currentUserSubstitution;
    @Autowired
    private UserProfileService userProfileService;
    @Autowired
    private UserAvatars userAvatars;
    @Autowired
    private ReportAccessService reportAccessService;
    @Autowired
    private CurrentAuthentication currentAuthentication;
    @ViewComponent
    private JmixListMenu menu;

    private final Span reportAccessBadge = new Span();
    /** The authentication of the user of the view: the events arrive without a reliable one. */
    private Authentication authentication;
    private boolean reportAccessShown;

    /**
     * The access management of the reports is in the menu for whoever has the role, but only those who manage
     * the access to a report keep it — the authors, the heads and the delegates of the owners — with the
     * number of the requests waiting for them.
     */
    @Subscribe
    public void onReady(final ReadyEvent event) {
        authentication = currentAuthentication.getAuthentication();
        ListMenu.MenuItem item = menu.getMenuItem(REPORT_ACCESS_ITEM);
        if (item == null) {
            return;
        }
        if (!reportAccessService.canOpenAccessManagement()) {
            if (menu.getMenuItem(REPORTS_MENU) instanceof ListMenu.MenuBarItem reports) {
                reports.removeChildItem(item);
            }
            return;
        }
        reportAccessBadge.getElement().getThemeList().addAll(List.of("badge", "pill", "small", "primary"));
        reportAccessBadge.addClassNames(LumoUtility.Margin.Left.SMALL);
        item.setSuffixComponent(reportAccessBadge);
        reportAccessShown = true;
        refreshReportAccessBadge();
    }

    /** A change of the access made by anyone: the requests waiting for this user are counted again. */
    @EventListener
    public void onReportAccessRequestsChanged(final ReportAccessUiEventBridge.RequestsChangedUiEvent event) {
        if (reportAccessShown) {
            ReportAccessUiEventBridge.runAs(authentication, this::refreshReportAccessBadge);
        }
    }

    private void refreshReportAccessBadge() {
        long pending = reportAccessService.countPendingRequests();
        reportAccessBadge.setText(String.valueOf(pending));
        // not setVisible: the display of the Lumo badge overrides the hidden attribute
        if (pending > 0) {
            reportAccessBadge.getStyle().remove("display");
        } else {
            reportAccessBadge.getStyle().setDisplay(Style.Display.NONE);
        }
    }

    @Install(to = "userMenu", subject = "buttonRenderer")
    private Component userMenuButtonRenderer(final UserDetails userDetails) {
        if (!(userDetails instanceof User user)) {
            return null;
        }

        String userName = generateUserName(user);

        Div content = uiComponents.create(Div.class);
        content.setClassName("user-menu-button-content");

        Avatar avatar = createAvatar(user);

        Span name = uiComponents.create(Span.class);
        name.setText(userName);
        name.setClassName("user-menu-text");

        content.add(avatar, name);

        if (isSubstituted(user)) {
            Span subtext = uiComponents.create(Span.class);
            subtext.setText(messages.getMessage("userMenu.substituted"));
            subtext.setClassName("user-menu-subtext");

            content.add(subtext);
        }

        return content;
    }

    @Install(to = "userMenu", subject = "headerRenderer")
    private Component userMenuHeaderRenderer(final UserDetails userDetails) {
        if (!(userDetails instanceof User user)) {
            return null;
        }

        Div content = uiComponents.create(Div.class);
        content.setClassName("user-menu-header-content");

        String name = generateUserName(user);

        Avatar avatar = createAvatar(user);
        avatar.addThemeVariants(AvatarVariant.LUMO_LARGE);

        Span text = uiComponents.create(Span.class);
        text.setText(name);
        text.setClassName("user-menu-text");

        content.add(avatar, text);

        if (name.equals(user.getUsername())) {
            text.addClassNames("user-menu-text-subtext");
        } else {
            Span subtext = uiComponents.create(Span.class);
            subtext.setText(user.getUsername());
            subtext.setClassName("user-menu-subtext");

            content.add(subtext);
        }

        return content;
    }

    private Avatar createAvatar(User user) {
        Avatar avatar = uiComponents.create(Avatar.class);
        // the profile photo, else the employee photo, else the initials
        userAvatars.showPhoto(avatar, userProfileService.getProfile(user.getUsername()));
        avatar.getElement().setAttribute("tabindex", "-1");
        avatar.setClassName("user-menu-avatar");

        return avatar;
    }

    private String generateUserName(User user) {
        String userName = String.format("%s %s",
                        Strings.nullToEmpty(user.getFirstName()),
                        Strings.nullToEmpty(user.getLastName()))
                .trim();

        return userName.isEmpty() ? user.getUsername() : userName;
    }

    private boolean isSubstituted(User user) {
        UserDetails authenticatedUser = currentUserSubstitution.getAuthenticatedUser();
        return user != null && !authenticatedUser.getUsername().equals(user.getUsername());
    }
}
