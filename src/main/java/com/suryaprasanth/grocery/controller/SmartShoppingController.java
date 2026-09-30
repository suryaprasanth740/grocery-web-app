package com.suryaprasanth.grocery.controller;

import com.suryaprasanth.grocery.dto.BudgetRequest;
import com.suryaprasanth.grocery.dto.BudgetSummary;
import com.suryaprasanth.grocery.dto.ListLine;
import com.suryaprasanth.grocery.dto.ListRequest;
import com.suryaprasanth.grocery.dto.RecipeSummary;
import com.suryaprasanth.grocery.dto.RecipeView;
import com.suryaprasanth.grocery.dto.ReorderSuggestion;
import com.suryaprasanth.grocery.model.User;
import com.suryaprasanth.grocery.repository.UserRepository;
import com.suryaprasanth.grocery.service.BudgetService;
import com.suryaprasanth.grocery.service.RecipeService;
import com.suryaprasanth.grocery.service.ReorderService;
import com.suryaprasanth.grocery.service.ShoppingListService;
import com.suryaprasanth.grocery.util.SessionUtil;
import jakarta.servlet.http.HttpSession;
import jakarta.validation.Valid;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * FreshCart's smart shopping features:
 *  - Paste a shopping list -> matched products   POST /api/smart-list/parse
 *  - Recipes and festival kits -> exact packs    GET  /api/recipes, /api/recipes/{id}?servings=6
 *  - Monthly budget mode                         GET/PUT /api/budget
 *  - "Running low?" reorder reminders            GET  /api/reorder
 */
@RestController
@RequestMapping("/api")
public class SmartShoppingController {

    private final ShoppingListService shoppingListService;
    private final RecipeService recipeService;
    private final BudgetService budgetService;
    private final ReorderService reorderService;
    private final UserRepository userRepository;

    public SmartShoppingController(ShoppingListService shoppingListService, RecipeService recipeService,
                                   BudgetService budgetService, ReorderService reorderService,
                                   UserRepository userRepository) {
        this.shoppingListService = shoppingListService;
        this.recipeService = recipeService;
        this.budgetService = budgetService;
        this.reorderService = reorderService;
        this.userRepository = userRepository;
    }

    /** Anyone can try it; adding to the cart needs a login. */
    @PostMapping("/smart-list/parse")
    public List<ListLine> parseList(@Valid @RequestBody ListRequest request) {
        return shoppingListService.parse(request.getText());
    }

    @GetMapping("/recipes")
    public List<RecipeSummary> recipes() {
        return recipeService.list();
    }

    @GetMapping("/recipes/{id}")
    public RecipeView recipe(@PathVariable String id, @RequestParam(required = false) Integer servings) {
        return recipeService.view(id, servings);
    }

    @GetMapping("/budget")
    public BudgetSummary budget(HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        return budgetService.summary(user);
    }

    @PutMapping("/budget")
    public BudgetSummary setBudget(@RequestBody BudgetRequest request, HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        budgetService.setBudget(user, request.getAmount());
        return budgetService.summary(user);
    }

    @GetMapping("/reorder")
    public List<ReorderSuggestion> reorder(HttpSession session) {
        User user = SessionUtil.requireLoggedInUser(session, userRepository);
        return reorderService.suggestions(user);
    }
}
