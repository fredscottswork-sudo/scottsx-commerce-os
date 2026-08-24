package com.scottsx.app.data

/**
 * Product Upload Safety Check
 *
 * Pre-publish validation that catches:
 *   - Missing required fields (name, price, category, image)
 *   - Suspect pricing (zero, negative, suspiciously large)
 *   - Prohibited keywords (weapons, drugs, counterfeit)
 *   - Too few / too many images
 *   - Suspicious description patterns
 *
 * Result surfaces as a list of [Issue]s with severity, which the
 * UI uses to gate the publish button (and explain WHY a listing
 * was rejected or sent to admin review).
 */
object ProductUploadSafety {

    enum class Severity { INFO, WARN, BLOCK }
    data class Issue(
        val severity: Severity,
        val code: String,
        val message: String,
    )
    data class Result(
        val issues: List<Issue>,
        val isPublishable: Boolean,
        val requiresAdminReview: Boolean,
    ) {
        val errors: List<Issue> = issues.filter { it.severity == Severity.BLOCK }
        val warnings: List<Issue> = issues.filter { it.severity == Severity.WARN }
        val infos: List<Issue> = issues.filter { it.severity == Severity.INFO }
    }

    data class Draft(
        val name: String,
        val description: String,
        val categoryName: String,
        val priceUgx: Long,
        val stock: Int,
        val imageCount: Int,
        val locationLabel: String = "",
    )

    // Categories that route to admin review for extra scrutiny.
    private val sensitiveCategories = setOf(
        "Health", "Beauty", "Medicine", "Pharmacy", "Supplement",
        "Baby", "Childcare", "Food", "Grocery", "Drink",
        "Electronics", "Mobile", "Phone",
    )

    // Words that automatically block a listing.
    private val prohibitedKeywords = listOf(
        "weapon", "gun", "rifle", "pistol", "ammunition", "bullet",
        "drug", "cocaine", "heroin", "meth", "narcotic",
        "counterfeit", "fake", "replica", "knockoff",
        "stolen", "illegal", "contraband",
        "prescription", "rx only",
        "explosive", "bomb", "grenade",
    )

    fun check(d: Draft): Result {
        val issues = mutableListOf<Issue>()

        // Required fields
        if (d.name.isBlank()) {
            issues += Issue(Severity.BLOCK, "name_missing", "Product name is required")
        } else if (d.name.length < 3) {
            issues += Issue(Severity.BLOCK, "name_too_short", "Name needs at least 3 characters")
        } else if (d.name.length > 120) {
            issues += Issue(Severity.WARN, "name_too_long", "Names over 120 characters get truncated")
        }

        if (d.description.isBlank()) {
            issues += Issue(Severity.WARN, "description_missing",
                "Add a description — listings with text sell 3x more")
        } else if (d.description.length < 20) {
            issues += Issue(Severity.WARN, "description_short",
                "Description is too short — aim for at least 20 characters")
        }

        if (d.categoryName.isBlank() || d.categoryName == "All") {
            issues += Issue(Severity.BLOCK, "category_missing", "Pick a category")
        }

        // Pricing
        if (d.priceUgx <= 0L) {
            issues += Issue(Severity.BLOCK, "price_invalid", "Price must be greater than 0")
        } else if (d.priceUgx > 5_000_000_000L) {
            // 5 billion UGX = unrealistic for individual listing
            issues += Issue(Severity.BLOCK, "price_too_high", "Price is suspiciously high")
        } else if (d.priceUgx < 1_000L) {
            issues += Issue(Severity.WARN, "price_low",
                "Price below UGX 1,000 — double-check this is correct")
        }

        // Stock
        if (d.stock < 0) {
            issues += Issue(Severity.BLOCK, "stock_negative", "Stock cannot be negative")
        } else if (d.stock == 0) {
            issues += Issue(Severity.WARN, "stock_zero", "Stock is 0 — buyers can't purchase")
        }

        // Images
        if (d.imageCount == 0) {
            issues += Issue(Severity.BLOCK, "no_images",
                "Add at least 1 photo — listings with photos sell 5x more")
        } else if (d.imageCount > 16) {
            issues += Issue(Severity.BLOCK, "too_many_images",
                "Maximum 16 photos — remove some")
        } else if (d.imageCount < 3) {
            issues += Issue(Severity.WARN, "few_images",
                "Add at least 3 photos to show the product from different angles")
        }

        // Location
        if (d.locationLabel.isBlank()) {
            issues += Issue(Severity.WARN, "no_location",
                "Add a location so buyers know where the item ships from")
        }

        // Prohibited keywords (case-insensitive substring match)
        val combined = "${d.name} ${d.description}".lowercase()
        val hit = prohibitedKeywords.firstOrNull { it in combined }
        if (hit != null) {
            issues += Issue(Severity.BLOCK, "prohibited",
                "Listing contains a prohibited term (\"$hit\"). This item can't be sold on ScottsTechX.")
        }

        // Suspicious pricing patterns (e.g., very round numbers)
        if (d.priceUgx > 0 && d.priceUgx % 1_000_000L == 0L && d.priceUgx >= 50_000_000L) {
            issues += Issue(Severity.WARN, "price_round_high",
                "Very round price on a high-value item — buyers may negotiate")
        }

        // Admin review for sensitive categories
        val needsReview = issues.none { it.severity == Severity.BLOCK } &&
            sensitiveCategories.any { d.categoryName.contains(it, ignoreCase = true) }

        val isPublishable = issues.none { it.severity == Severity.BLOCK }

        return Result(
            issues = issues,
            isPublishable = isPublishable,
            requiresAdminReview = needsReview,
        )
    }
}