package ru.spb.reshenie.chekerstatus.web.controller;

import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.support.RedirectAttributes;
import ru.spb.reshenie.chekerstatus.security.model.ManagedUser;
import ru.spb.reshenie.chekerstatus.security.service.UserManagementService;

import java.util.List;
import java.util.Set;

@Controller
public class AdminUserController {

    private final UserManagementService userManagementService;

    public AdminUserController(UserManagementService userManagementService) {
        this.userManagementService = userManagementService;
    }

    @GetMapping("/admin/users")
    public String users(Model model, Authentication authentication) {
        model.addAttribute("active", "users");
        model.addAttribute("users", userManagementService.findAllUsers());
        model.addAttribute("currentUsername", currentUsername(authentication));
        return "admin-users";
    }

    @GetMapping("/admin/users/new")
    public String newUser(Model model, Authentication authentication) {
        populateUserFormModel(model, authentication, emptyUser(), false);
        model.addAttribute("pageTitle", "Новый пользователь");
        model.addAttribute("pageSubtitle", "Создание пользователя, назначение ролей и точечных прав доступа");
        model.addAttribute("formAction", "/admin/users");
        model.addAttribute("submitLabel", "Создать пользователя");
        return "admin-user-form";
    }

    @GetMapping("/admin/users/{id}")
    public String userCard(@PathVariable("id") long id, Model model, Authentication authentication) {
        ManagedUser user = userManagementService.findUser(id);
        if (user == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND);
        }

        populateUserFormModel(model, authentication, user, true);
        model.addAttribute("pageTitle", "Карточка пользователя");
        model.addAttribute("pageSubtitle", "Настройка ролей и точечных прав доступа");
        model.addAttribute("formAction", "/admin/users/" + id);
        model.addAttribute("submitLabel", "Сохранить изменения");
        return "admin-user-form";
    }

    @PostMapping("/admin/users")
    public String createUser(@RequestParam("username") String username,
                             @RequestParam(name = "displayName", required = false) String displayName,
                             @RequestParam("password") String password,
                             @RequestParam(name = "enabled", defaultValue = "false") boolean enabled,
                             @RequestParam(name = "roleCodes", required = false) List<String> roleCodes,
                             @RequestParam(name = "permissionCodes", required = false) List<String> permissionCodes,
                             RedirectAttributes redirectAttributes) {
        try {
            long userId = userManagementService.createUser(
                    username,
                    displayName,
                    password,
                    enabled,
                    roleCodes,
                    permissionCodes
            );
            redirectAttributes.addFlashAttribute("message", "Пользователь создан.");
            return "redirect:/admin/users/" + userId;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return "redirect:/admin/users/new";
        }
    }

    @PostMapping("/admin/users/{id}")
    public String updateUser(@PathVariable("id") long id,
                             @RequestParam("username") String username,
                             @RequestParam(name = "displayName", required = false) String displayName,
                             @RequestParam(name = "password", required = false) String password,
                             @RequestParam(name = "enabled", defaultValue = "false") boolean enabled,
                             @RequestParam(name = "roleCodes", required = false) List<String> roleCodes,
                             @RequestParam(name = "permissionCodes", required = false) List<String> permissionCodes,
                             RedirectAttributes redirectAttributes) {
        try {
            userManagementService.updateUser(id, username, displayName, password, enabled, roleCodes, permissionCodes);
            redirectAttributes.addFlashAttribute("message", "Пользователь обновлён.");
            return "redirect:/admin/users/" + id;
        } catch (IllegalArgumentException e) {
            redirectAttributes.addFlashAttribute("error", e.getMessage());
            return userManagementService.findUser(id) == null
                    ? "redirect:/admin/users"
                    : "redirect:/admin/users/" + id;
        }
    }

    private void populateUserFormModel(Model model,
                                       Authentication authentication,
                                       ManagedUser user,
                                       boolean editing) {
        model.addAttribute("active", "users");
        model.addAttribute("managedUser", user);
        model.addAttribute("roles", userManagementService.findAllRoles());
        model.addAttribute("permissions", userManagementService.findAllPermissions());
        model.addAttribute("currentUsername", currentUsername(authentication));
        model.addAttribute("editing", Boolean.valueOf(editing));
    }

    private ManagedUser emptyUser() {
        return new ManagedUser(0L, "", "", true, Set.of(), Set.of(), Set.of());
    }

    private String currentUsername(Authentication authentication) {
        return authentication == null ? null : authentication.getName();
    }
}
