/**
 * Class that is used to represent the shopping list item view
 */
class ShoppingListItemView
@JvmOverloads
constructor(context: Context, attrs: AttributeSet? = null, defStyle: Int = 0)
	: ConstraintLayout(context, attrs, defStyle) {

	/* *********************
	 * COMPANION OBJECT
	 * ******************* */

	companion object {
		val TAG: String = ShoppingListItemView::class.java.simpleName
	}

	/* *********************
	 * CLASS VARIABLES
	 * ******************* */

	private var book: Book? = null
	private lateinit var shoppingListRow: ShoppingListRow
	private lateinit var shoppingItem: ShoppingItem
	private var isAnimating: Boolean = false
	private var isNotesOnScreen: Boolean = false
	private var constraintsChanged: Boolean = false
	private var isChecked: Boolean = false

	var isExpanded: Boolean
		get() = shoppingListRow.isExpanded
		set(value) {
			shoppingListRow.isExpanded = value
		}

	/**
	 * Injections
	 */
	private val dimensions: Dimensions by lazy { Dimensions_.getInstance_(context) }
	private val picassoUtils: PicassoUtils by lazy { PicassoUtils_.getInstance_(context) }
	private val shoppingListHandler: ShoppingListHandler by lazy {
		ShoppingListHandler_.getInstance_(context)
	}
	private val userData: UserData by lazy { UserData_.getInstance_(context) }

	/**
	 * Dimension Res
	 */
	private val itemRadius: Float by lazy { resources.getDimension(R.dimen.item_radius) }

	/**
	 * Views
	 */
	private var shoppingListItemLayout: ConstraintLayout
	private var actionableIconView: ActionableIconView
	private var itemImageView: ImageView
	private var itemPureImageView: ImageView
	private var greyScaleLayout: FrameLayout
	private var titleTextView: MaterialTextView
	private var priceLayout: LinearLayout
	private var priceTextView: MaterialTextView
	private var priceKgTextView: MaterialTextView
	private var promotionGroup: Group
	private var promotionImageView: ImageView
	private var promotionTextView: MaterialTextView
	private var tagTextView: MaterialTextView
	private var quantityValue: MaterialTextView
	private var quantityUnit: MaterialTextView
	private var quantity: ConstraintLayout
	private var quantityTotal: LinearLayout
	private var quantityTotalValue: MaterialTextView
	private var notes: ConstraintLayout
	private var noteDesc: MaterialTextView
	private var itemOptionsExpand: FrameLayout
	private var expandIcon: ImageView
	private var container: ConstraintLayout
	private var itemOptions: ConstraintLayout

	/* *********************
	 * INITIALIZE
	 * ******************* */

	/**
	 * Initialize View
	 */
	init {
		layoutParams = LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)

		// Set layout view
		inflate(context, R.layout.view_shopping_list_item, this)

		// Get views
		container = findViewById(R.id.container)
		shoppingListItemLayout = findViewById(R.id.shopping_list_item_layout)
		actionableIconView = findViewById(R.id.actionable_icon_view)
		itemImageView = findViewById(R.id.item_image_view)
		itemPureImageView = findViewById(R.id.item_pure_image_view)
		greyScaleLayout = findViewById(R.id.grey_scale_layout)
		titleTextView = findViewById(R.id.title_text_view)
		priceLayout = findViewById(R.id.price_layout)
		priceTextView = findViewById(R.id.price_text_view)
		priceKgTextView = findViewById(R.id.price_kg_text_view)
		promotionGroup = findViewById(R.id.promotion_group)
		promotionImageView = findViewById(R.id.promotion_image_view)
		promotionTextView = findViewById(R.id.promotion_text_view)
		tagTextView = findViewById(R.id.tag_text_view)

		// Item options views
		itemOptions = findViewById(R.id.item_options)
		expandIcon = findViewById(R.id.expand_icon)
		itemOptionsExpand = findViewById(R.id.item_options_expand)
		notes = findViewById(R.id.notes)
		noteDesc = findViewById(R.id.notes_desc)
		quantity = findViewById(R.id.quantity)
		quantityUnit = findViewById(R.id.quantity_unit)
		quantityValue = findViewById(R.id.quantity_edit_title)
		quantityTotal = findViewById(R.id.quantity_total)
		quantityTotalValue = findViewById(R.id.total_value)

		// Setup click listeners
		setClickListeners()
	}

	/**
	 * Set click listeners
	 */
	private fun setClickListeners() {
		// Set shopping list item layout click
		shoppingListItemLayout.setOnClickListener {
			val item = shoppingItem.item

			// Check if item statusID is an error status
			if (isError(shoppingItem, item)) {
				return@setOnClickListener
			}

			if (book != null) {
				// Log book open analytic event
				EventLoggingService.logEvent(context, ShoppingListAnalyticEvents.shoppingListAnalyticEvent(
						ShoppingListAnalyticEvents.ITEM, ShoppingListAnalyticEvents.OPEN_ACTION))

				// Post shopping list item click event
				EventBus.getDefault().post(ShoppingListItemClickEvent(
						item = item, shoppingItem = shoppingItem, sourceID = Constants.SRC_SHOPPING_LIST))
			}
		}

		// Prevent long press "leak" and double haptic
		shoppingListItemLayout.setOnLongClickListener { true }
		shoppingListItemLayout.isHapticFeedbackEnabled = false

		// Set actionable icon view click
		actionableIconView.setOnClickListener {
			// Start item modify
			if (shoppingListHandler.modifyingItem(shoppingItem.item.itemID, false)) {
				// Update checked state
				shoppingItem.isChecked = !shoppingItem.isChecked

				// Update checked state
				updateCheckedState()

				// Log item check analytic event
				EventLoggingService.logEvent(context, ShoppingListAnalyticEvents.shoppingListAnalyticEvent(
						ShoppingListAnalyticEvents.ITEM, ShoppingListAnalyticEvents.CHECK_ACTION)
						.putCheck(if (shoppingItem.isChecked) ShoppingListAnalyticEvents.CHECK
						else ShoppingListAnalyticEvents.UNCHECK))

				// Post shopping list item check event
				EventBus.getDefault().post(
						ShoppingListItemCheckEvent(shoppingItem.id, shoppingItem.isChecked))
			}
		}

		// Item options expands / collapses on click and rotate the arrow
		itemOptionsExpand.setOnClickListener {
			if (!isExpanded && !isAnimating) expand()
			else if (!isAnimating && isExpanded) animateCollapsedState()
		}

		// Show bottom sheet to edit quantity
		quantity.setOnClickListener {
			EventBus.getDefault().post(ShoppingListItemOptionsClickEvent(shoppingItem.id, true,
					isExpanded))
		}

		// Show bottom sheet to edit note
		notes.setOnClickListener {
			EventBus.getDefault().post(ShoppingListItemOptionsClickEvent(shoppingItem.id, false,
					isExpanded))
		}
	}

	/* *********************
	 * CONVENIENCE METHODS
	 * ******************* */

	/**
	 * Binds data to the view and sets UI
	 *
	 * @param shoppingListRow object holding shopping list row
	 * @param book           object holding book data (store refreshed)
	 */
	fun bind(shoppingListRow: ShoppingListRow, book: Book?) {
		this.shoppingListRow = shoppingListRow
		this.shoppingItem = shoppingListRow.shoppingItem!!
		val item = shoppingItem.item
		this.book = book

		// Setup Item Options
		// Set note
		if (StringUtils.isValidString(shoppingItem.note)) noteDesc.text = shoppingItem.note
		else noteDesc.text = null
//		if (isNotesOnScreen) adjustNotesHeight()

		// Set quantity
		if (shoppingItem.quantity != 0) {

			quantityValue.text = shoppingItem.quantity.toString()
			quantityValue.hint = null

			if (shoppingItem.item.priceUnit == null) {
				// When item price info is unavailable
				quantityTotal.visibility = View.GONE
				quantityUnit.text = context.resources.getString(R.string.quantity_item)
				quantityUnit.visibility = View.VISIBLE
			} else {
				quantityUnit.text = shoppingItem.quantityUnit
				quantityUnit.visibility = View.VISIBLE
				quantityTotal.visibility = View.GONE
				quantityTotalValue.visibility = View.GONE

				val total = Utils.getItemTotal(shoppingItem)
				if (Utils.getItemUnit(shoppingItem.item.priceUnit) == shoppingItem.quantityUnit
						&& total != "$0.0") {
					quantityTotal.visibility = View.VISIBLE
					quantityTotalValue.visibility = View.VISIBLE
					quantityTotalValue.text = total
				}
			}
		} else {
			quantityValue.text = null
			quantityValue.hint = context.getString(R.string.add_quantity)
			quantityUnit.visibility = View.GONE
			quantityTotal.visibility = View.GONE
		}

		// Set item options to collapsed
		if (isExpanded) setExpanded()
		else setCollapsed()

		showExpandArrow()
		isChecked = false
		val stateSet = intArrayOf(android.R.attr.state_checked * if (isChecked) 1 else -1)
		expandIcon.setImageState(stateSet, true)

		// Title different UI for Landscape or Tablets
		titleTextView.maxLines = if (dimensions.isLandscape || dimensions.isTablet) {
			// Needed to ensure android:ellipsize="end" works correctly
			titleTextView.setHorizontallyScrolling(true)
			1
		} else {
			// Needed to ensure multiline text works correctly
			titleTextView.setHorizontallyScrolling(false)
			2
		}

		// Needed to ensure android:ellipsize="end" works correctly
		priceTextView.setHorizontallyScrolling(true)
		priceKgTextView.setHorizontallyScrolling(true)
		tagTextView.setHorizontallyScrolling(true)
		promotionTextView.setHorizontallyScrolling(true)

		// Update checked state
		updateCheckedState()

		// Get item image Url
		val pureImageUrl = if (StringUtils.isValidString(item.itemPureAssetUrl))
			ImageUtils.ImageAsset.item().getUrl(
					item.itemID, false, item.assetVersion, item.itemPureAssetUrl) else null

		// Get item focus image Url
		val focusImageUrl = ImageUtils.ImageAsset.itemFocus().getUrl(
				item.itemID, true, item.focusAssetVersion, item.itemFocusAssetUrl)

		// Set item image
		// Picasso request
		val pureImage = StringUtils.isValidString(pureImageUrl)
		itemImageView.visibility = if (pureImage) View.GONE else View.VISIBLE
		itemPureImageView.visibility = if (pureImage) View.VISIBLE else View.GONE
		if (pureImage) {
			picassoUtils.picasso
					.load(pureImageUrl)
					.networkPolicy(NetworkPolicy.OFFLINE)
					.resizeDimen(R.dimen.item_image_size, R.dimen.item_image_size)
					.into(itemPureImageView, object : Utils.PicassoCallback() {
						override fun onError() {
							picassoUtils.picasso
									.load(pureImageUrl)
									.resizeDimen(R.dimen.item_image_size, R.dimen.item_image_size)
									.into(itemPureImageView)
						}
					})
		} else {
			picassoUtils.picasso
					.load(focusImageUrl)
					.networkPolicy(NetworkPolicy.OFFLINE)
					.resizeDimen(R.dimen.item_image_size, R.dimen.item_image_size)
					.transform(RoundedSquareTransformation(itemRadius))
					.into(itemImageView, object : Utils.PicassoCallback() {
						override fun onError() {
							picassoUtils.picasso
									.load(focusImageUrl)
									.resizeDimen(R.dimen.item_image_size, R.dimen.item_image_size)
									.transform(RoundedSquareTransformation(itemRadius))
									.into(itemImageView)
						}
					})
		}

		// Set title
		titleTextView.text = Utils.getItemTitle(item)

		// Set price
		val price = Utils.getPriceAsText(item)
		val priceKg = Utils.getPriceKgAsText(item)
		priceKgTextView.visibility = if (StringUtils.isValidString(priceKg)) {
			priceKgTextView.text = priceKg
			View.VISIBLE
		} else {
			View.GONE
		}
		priceLayout.visibility = if (StringUtils.isValidString(price)) {
			priceTextView.text = price
			View.VISIBLE
		} else {
			View.GONE
		}

		// Set tag
		val statusID = Utils.getItemStatusID(resources, shoppingItem, book)
		val tag = Utils.getItemTag(resources, item, book)
		tagTextView.visibility = if (StringUtils.isValidString(tag)) {
			tagTextView.text = tag
			tagTextView.setTextColor(Utils.getTagUrgencyTextColor(context, tag))
			View.VISIBLE
		} else {
			View.GONE
		}

		// Set grey scale
		val theme = ThemeUtils.getTheme(context.resources, userData.theme)
		val greyScale = statusID == Constants.EXPIRED || statusID == Constants.DISABLED
				|| statusID == Constants.OUT_OF_REGION
		itemImageView.colorFilter = ThemeUtils.getGreyColorFilter(statusID)
		itemPureImageView.colorFilter = ThemeUtils.getGreyColorFilter(statusID)
		greyScaleLayout.visibility =
				if (greyScale && theme == ThemeUtils.LIGHT) View.VISIBLE else View.GONE

		// Set promotion
		val promotions = item!!.promotions
		promotionGroup.visibility = if (promotions != null && promotions.isNotEmpty()) {
			val promotion = promotions[0]

			// Set promotion title
			val promotionTitle = Utils.getPromotionTitle(promotion)
			if (promotion.showPromoGroupShow() && StringUtils.isValidString(promotionTitle)) {
				promotionTextView.text = promotionTitle

				// Set promotion icon
				promotionImageView.setImageDrawable(
						Utils.getDrawableFromVectorDrawable(context, R.drawable.icon_other))
				VISIBLE
			} else {
				GONE
			}
		} else {
			GONE
		}
	}

	/**
	 * Update checked state
	 */
	fun updateCheckedState() {
		// Set the actionable icon view state
		actionableIconView.setActionableIconState(shoppingItem.isChecked)

		// Set text strike through for checked items
		setStrikeThrough(shoppingItem.isChecked)
	}

	/**
	 * Sets state of text strike through when an item is checked as unchecked
	 *
	 * @param isChecked is the item checked
	 */
	private fun setStrikeThrough(isChecked: Boolean) {
		if (isChecked) {
			titleTextView.paintFlags = titleTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
			priceTextView.paintFlags = priceTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
			tagTextView.paintFlags = tagTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
			promotionTextView.paintFlags = promotionTextView.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
			noteDesc.paintFlags = noteDesc.paintFlags or Paint.STRIKE_THRU_TEXT_FLAG
		} else {
			titleTextView.paintFlags = titleTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
			priceTextView.paintFlags = priceTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
			tagTextView.paintFlags = tagTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
			promotionTextView.paintFlags =
					promotionTextView.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
			noteDesc.paintFlags = noteDesc.paintFlags and Paint.STRIKE_THRU_TEXT_FLAG.inv()
		}

		titleTextView.postInvalidate()
		priceTextView.postInvalidate()
		tagTextView.postInvalidate()
		promotionTextView.postInvalidate()
		noteDesc.postInvalidate()
	}

	/**
	 * Check if item statusID is an error status
	 *
	 * @param shoppingItem object holding shopping item data
	 * @param item object holding item data
	 * @return whether or not item statusID is an error status
	 */
	private fun isError(shoppingItem: ShoppingItem, item: Item): Boolean {
		val statusID = Utils.getItemStatusID(resources, shoppingItem, book)

		// Post shopping list item click error event
		if (statusID == Constants.EXPIRED || statusID == Constants.DISABLED
				|| statusID == Constants.OUT_OF_REGION) {
			EventBus.getDefault().post(ShoppingListItemClickEvent(statusID, item))
			return true
		}
		return false
	}

	/**
	 * Expand all item options (slides notes and quantity down and rotate arrow
	 */
	private fun expand() {
		if (!isAnimating && !isExpanded) {
			isExpanded = true
			isAnimating = true
			isNotesOnScreen = true

			// Create height animation
			val heightAnimation: ValueAnimator = slideAnimator(container.height,
					shoppingListItemLayout.height + notes.height + quantity.height)
			heightAnimation.addListener(object : Animator.AnimatorListener {
				override fun onAnimationRepeat(animation: Animator?) {}
				override fun onAnimationCancel(animation: Animator?) {}
				override fun onAnimationStart(animation: Animator?) {}
				override fun onAnimationEnd(animation: Animator?) {
					isAnimating = false

					if (constraintsChanged) {
						// Revert constraints back to original
						val containerConstraints = ConstraintSet()
						containerConstraints.clone(container)
						containerConstraints.clear(itemOptions.id, ConstraintSet.TOP)
						containerConstraints.applyTo(container)

						val optionsConstraints = ConstraintSet()
						optionsConstraints.clone(itemOptions)
						optionsConstraints.clear(notes.id, ConstraintSet.BOTTOM)
						optionsConstraints.clear(notes.id, ConstraintSet.TOP)
						optionsConstraints.connect(notes.id, ConstraintSet.BOTTOM, quantity.id, ConstraintSet.TOP)
						optionsConstraints.applyTo(itemOptions)
						constraintsChanged = false

						// Revert layout params back to original
						with(itemOptions.layoutParams) {
							this.height = ViewGroup.LayoutParams.WRAP_CONTENT
							itemOptions.layoutParams = this
						}
					}
				}
			})

			// Start animations
			animateExpandArrow()
			heightAnimation.start()

			// Post shopping list expand item event
			EventBus.getDefault().post(ShoppingListExpandItemEvent(shoppingItem.id))
		}
	}

	/**
	 * Set view height to have item options expanded without an animation
	 */
	private fun setExpanded() {
		post {
			isExpanded = true

			// Set container to be fully expanded
			with(container.layoutParams) {
				height = shoppingListItemLayout.height + quantity.height + notes.height
				container.layoutParams = this
			}
			// If constraints were changed to show notes revert them to original format
			if (constraintsChanged) {
				val optionsConstraints = ConstraintSet()
				optionsConstraints.clone(itemOptions)
				optionsConstraints.clear(notes.id, ConstraintSet.TOP)
				optionsConstraints.connect(notes.id, ConstraintSet.BOTTOM, quantity.id, ConstraintSet.TOP)
				optionsConstraints.applyTo(itemOptions)

				val containerConstraints = ConstraintSet()
				containerConstraints.clone(container)
				containerConstraints.clear(itemOptions.id, ConstraintSet.TOP)
				containerConstraints.applyTo(container)
				constraintsChanged = false
			}
			showExpandArrow()

			// Post shopping list expand item event
			EventBus.getDefault().post(ShoppingListExpandItemEvent(shoppingItem.id))
		}
	}

	/**
	 * Collapses item options to only show necessary information
	 */
	fun animateCollapsedState() {
		if (!StringUtils.isValidString(shoppingItem.note) && shoppingItem.quantity != 0) collapse(true)
		if (StringUtils.isValidString(shoppingItem.note) && shoppingItem.quantity == 0) collapse(false, true)
		else collapse()
	}

	/**
	 * Set view height to have item options collapsed without an animation
	 */
	fun setCollapsed() {
		post {
			isExpanded = false

			// Has both note and quantity
			if (StringUtils.isValidString(shoppingItem.note) && shoppingItem.quantity != 0) {
				with(container.layoutParams) {
					height = shoppingListItemLayout.height + notes.height + quantity.height
					container.layoutParams = this
				}
			} // Has quantity but not note
			else if (!StringUtils.isValidString(shoppingItem.note) && shoppingItem.quantity != 0) {
				with(container.layoutParams) {
					height = shoppingListItemLayout.height + quantity.height
					container.layoutParams = this
				}
			} // Has note but not quantity
			else if (StringUtils.isValidString(shoppingItem.note) && shoppingItem.quantity == 0) {

				// Set constraints for animation
				val containerConstraints = ConstraintSet()
				containerConstraints.clone(container)
				containerConstraints.connect(itemOptions.id, ConstraintSet.TOP, shoppingListItemLayout.id, ConstraintSet.BOTTOM)
				containerConstraints.applyTo(container)

				val optionsConstraints = ConstraintSet()
				optionsConstraints.clone(itemOptions)
				optionsConstraints.clear(notes.id, ConstraintSet.BOTTOM)
				optionsConstraints.connect(notes.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
				optionsConstraints.applyTo(itemOptions)

				// Set layout params
				with(itemOptions.layoutParams) {
					this.height = context.resources.getDimension(R.dimen.item_options_width).toInt()
					itemOptions.layoutParams = this
				}

				// Set container height
				with(container.layoutParams) {
					height = shoppingListItemLayout.height + notes.height
					container.layoutParams = this
				}
				constraintsChanged = true
			}
			// Note and quantity are null
			else {
				with(container.layoutParams) {
					height = shoppingListItemLayout.height
					container.layoutParams = this
				}
			}
			showExpandArrow()
		}
	}

	/**
	 * Collapse the correct item options
	 */
	private fun collapse(showQuantity: Boolean = false, showNotes: Boolean = false) {

		if (!isAnimating && isExpanded) {
			isExpanded = false
			isAnimating = true

			// Create height animation
			val heightAnimation: ValueAnimator
			if (showQuantity) {
				heightAnimation = slideAnimator(container.height, shoppingListItemLayout.height +
						quantity.height)
			} else if (showNotes) {
				heightAnimation = slideAnimator(container.height, shoppingListItemLayout.height +
						notes.height)
			} else {
				heightAnimation = slideAnimator(container.height, shoppingListItemLayout.height)
			}
			heightAnimation.addListener(object : Animator.AnimatorListener {
				override fun onAnimationRepeat(animation: Animator?) {}
				override fun onAnimationCancel(animation: Animator?) {}
				override fun onAnimationStart(animation: Animator?) {}
				override fun onAnimationEnd(animation: Animator?) {
					isAnimating = false
					isNotesOnScreen = showNotes
				}
			})

			if (showNotes) {

				// Set constraints for animation
				val containerConstraints = ConstraintSet()
				containerConstraints.clone(container)
				containerConstraints.connect(itemOptions.id, ConstraintSet.TOP, shoppingListItemLayout.id, ConstraintSet.BOTTOM)
				containerConstraints.applyTo(container)

				val optionsConstraints = ConstraintSet()
				optionsConstraints.clone(itemOptions)
				optionsConstraints.clear(notes.id, ConstraintSet.BOTTOM)
				optionsConstraints.connect(notes.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
				optionsConstraints.applyTo(itemOptions)

				// Set layout params
				with(itemOptions.layoutParams) {
					this.height = context.resources.getDimension(R.dimen.item_options_width).toInt()
					itemOptions.layoutParams = this
				}
				constraintsChanged = true
			}

			// Start animations
			animateExpandArrow()
			heightAnimation.start()
		}
	}

	/**
	 * Animates sliding animation for item options
	 *
	 * @param start animation value start
	 * @param end   animation value end
	 */
	private fun slideAnimator(start: Int, end: Int): ValueAnimator {
		val animator: ValueAnimator = ValueAnimator.ofInt(start, end)
		animator.addUpdateListener {
			// Update height
			val value = it.animatedValue as Int
			with(container.layoutParams) {
				height = value
				container.layoutParams = this
			}
		}
		animator.duration = Constants.DELAY_QUANTITY
		return animator
	}

	/**
	 * Adjusts note view to fit information it holds
	 */
	private fun adjustNotesHeight() {
		// Measure height before expanding
		val widthSpec = MeasureSpec.makeMeasureSpec(notes.measuredWidth, MeasureSpec.EXACTLY)
		val heightSpec = MeasureSpec.makeMeasureSpec(0, MeasureSpec.UNSPECIFIED)
		notes.measure(widthSpec, heightSpec)

		with(notes.layoutParams) {
			height = notes.measuredHeight
			notes.layoutParams = this
		}
	}

	/**
	 * Decide if you should show expand arrow in item options
	 */
	private fun showExpandArrow() {
		if (shoppingItem.quantity != 0 && StringUtils.isValidString(shoppingItem.note)) {
			itemOptionsExpand.isEnabled = false
			expandIcon.visibility = View.GONE
		} else {
			expandIcon.visibility = View.VISIBLE
			itemOptionsExpand.isEnabled = true
		}
	}

	/**
	 * Flip expand arrow by starting vector drawable animation
	 */
	private fun animateExpandArrow() {
		post {
			isChecked = !isChecked
			val stateSet = intArrayOf(android.R.attr.state_checked * if (!isChecked) 1 else -1)
			expandIcon.setImageState(stateSet, true)
		}
	}

	/**
	 * Get shopping item ID
	 */
	fun getShoppingItemID(): Long? {
		return if (::shoppingItem.isInitialized) shoppingItem.id
		else null
	}
}
