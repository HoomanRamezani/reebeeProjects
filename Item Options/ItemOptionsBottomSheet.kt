/**
 * This Bottom Sheet handles editing item options
 */
class ItemOptionsBottomSheet : BaseBottomSheet() {

  /* *********************
   * COMPANION OBJECT
   * ******************* */

  companion object {
    val TAG: String = ItemOptionsBottomSheet::class.java.simpleName
  }

  /* *********************
   * CLASS VARIABLES
   * ******************* */

  // Arguments
  private var shoppingItem: ShoppingItem? = null
  private var isQuantity: Boolean = false
  private var isExpanded: Boolean = true
  private var unitSelected: String = "Items"

  // Instance States
  private var update: Boolean = false
  private var wasChangingConfig: Boolean = false

  /**
   * Injections
   */
  private val dimensions: Dimensions by lazy { Dimensions_.getInstance_(context) }
  private val shoppingListHandler: ShoppingListHandler by lazy {
    ShoppingListHandler_.getInstance_(context)
  }

  /**
   * Views
   */
  private lateinit var container: CoordinatorLayout
  private lateinit var scrollView: NestedScrollView
  private lateinit var title: MaterialTextView
  private lateinit var icon: ImageView
  private lateinit var editText: OnBackEditText
  private lateinit var actionLayout: FrameLayout
  private lateinit var actionImageView: ImageView
  private lateinit var unitStandard: MaterialButton
  private lateinit var unitItem: MaterialButton

  /* *********************
   * OVERRIDE METHODS
   * ******************* */

  /**
   * Bottom Sheet Created
   *
   * @param args a mapping from String keys to various values
   */
  override fun onCreate(args: Bundle?) {
    // Injections
    injectBottomSheetArguments()

    // Restore state
    restoreSavedInstanceState(args)
    super.onCreate(args)
  }

  /**
   * Inject Bottom Sheet Arguments
   */
  private fun injectBottomSheetArguments() {
    val args = arguments ?: return
    shoppingItem = args.getParcelable<ShoppingItem>("shoppingItem")
    isQuantity = args.getBoolean("isQuantity")
    isExpanded = args.getBoolean("isExpanded")

    // If you are updating the existing value set update to true
    if ((isQuantity && shoppingItem!!.quantity != 0) ||
        (!isQuantity && StringUtils.isValidString(shoppingItem!!.note))) update = true
  }

  /**
   * Restore Bottom Sheet state
   *
   * @param args a mapping from String keys to various values
   */
  private fun restoreSavedInstanceState(args: Bundle?) {
    if (args == null) {
      return
    }
    shoppingItem = args.getParcelable<ShoppingItem>("shoppingItem")
    isQuantity = args.getBoolean("isQuantity")
    isExpanded = args.getBoolean("isExpanded")
    update = args.getBoolean("update")
    wasChangingConfig = args.getBoolean("wasChangingConfig")
  }

  /**
   * Save Bottom Sheet state
   *
   * @param args a mapping from String keys to various values
   */
  override fun onSaveInstanceState(args: Bundle) {
    super.onSaveInstanceState(args)
    args.putParcelable("shoppingItem", shoppingItem)
    args.putBoolean("isQuantity", isQuantity)
    args.putBoolean("isExpanded", isExpanded)
    args.putBoolean("update", update)
    args.putBoolean("wasChangingConfig", wasChangingConfig)
  }

  /**
   * Set Bottom Sheet theme
   */
  override fun getTheme(): Int = R.style.BottomSheet

  /**
   * Bottom Sheet Dialog Created
   *
   * @param args a mapping from String keys to various values
   * @return created Dialog
   */
  override fun onCreateDialog(args: Bundle?): Dialog {
    val bottomSheet = super.onCreateDialog(args) as BottomSheetDialog

    bottomSheet.setOnShowListener { dialog ->
      val internalBottomSheet = (dialog as BottomSheetDialog)
          .findViewById<FrameLayout>(com.google.android.material.R.id.design_bottom_sheet)
      val bottomSheetBehavior = BottomSheetBehavior.from(internalBottomSheet!!)
      bottomSheetBehavior.state = BottomSheetBehavior.STATE_EXPANDED
      bottomSheetBehavior.addBottomSheetCallback(bottomSheetBehaviorCallback)
    }

    // Set keyboard behaviour
    bottomSheet.window?.setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE)
    return bottomSheet
  }

  /**
   * Bottom Sheet behaviour callback to dismiss Bottom Sheet on new state
   */
  private val bottomSheetBehaviorCallback = object : Utils.BottomSheetBehaviorCallback() {
    override fun onStateChanged(bottomSheet: View, newState: Int) {
      if (newState != BottomSheetBehavior.STATE_DRAGGING
          && newState != BottomSheetBehavior.STATE_EXPANDED) {
        safeDismiss()
      }
    }
  }

  /**
   * View Created
   *
   * @param layoutInflater view inflater
   * @param viewGroup      Bottom Sheet view container
   * @param args           a mapping from String keys to various values
   * @return the created view
   */
  @Nullable
  override fun onCreateView(layoutInflater: LayoutInflater, @Nullable viewGroup: ViewGroup?,
                            @Nullable args: Bundle?): View? {
    val view = layoutInflater.inflate(R.layout.bottom_sheet_item_options, viewGroup, false)

    // Get views
    container = view.findViewById(R.id.container)
    scrollView = view.findViewById(R.id.scroll_view)
    title = view.findViewById(R.id.title)
    icon = view.findViewById(R.id.numbers_icon)
    editText = view.findViewById(R.id.option_edit_text)
    actionLayout = view.findViewById(R.id.action_layout)
    actionImageView = view.findViewById(R.id.action_image_view)
    unitStandard = view.findViewById(R.id.unit_standard)
    unitItem = view.findViewById(R.id.unit_items)

    // Setup view
    if (isQuantity) {
      title.text = context!!.getString(R.string.quantity)
      icon.setImageResource(R.drawable.ic_quantity)
      editText.hint = context!!.getString(R.string.add_quantity)
      editText.inputType = InputType.TYPE_CLASS_NUMBER


      // Set up buttons to select unit the users quantity is in terms of
      if (shoppingItem != null) {

        val unit: String = if (shoppingItem!!.item.priceUnit == null)
                              context!!.resources.getString(R.string.quantity_item)
                           else Utils.getItemUnit(shoppingItem!!.item.priceUnit)
        unitSelected = shoppingItem!!.quantityUnit

        // Set unit button layout and text
        if (unit == context!!.resources.getString(R.string.quantity_item)) {
          unitItem.visibility = View.VISIBLE
          unitStandard.visibility = View.GONE
        } else {
          unitStandard.text = unit
          unitStandard.visibility = View.VISIBLE
          unitItem.visibility = View.VISIBLE
        }

        // Set which unit button is selected
        if (unitSelected == unitStandard.text) {
          unitStandard.strokeColor = ContextCompat.getColorStateList(context!!, R.color.border_primary)
          unitItem.strokeColor = ContextCompat.getColorStateList(context!!, R.color.button_border)
        } else {
          unitStandard.strokeColor = ContextCompat.getColorStateList(context!!, R.color.button_border)
          unitItem.strokeColor = ContextCompat.getColorStateList(context!!, R.color.border_primary)
        }
      }
    } else {
      unitStandard.visibility = View.GONE
      unitItem.visibility = View.GONE
    }
    setClickListeners()
    setupField()
    setActionLayoutState(editText.text.toString())

    // Scroll to top of nested scroll view
    scrollView.parent.requestChildFocus(scrollView, scrollView)
    return view
  }

  /**
   * Set click listeners
   */
  private fun setClickListeners() {
    // Set container click
    container.setOnClickListener { safeDismiss() }

    // Set action layout click
    actionLayout.setOnClickListener {
      editItem(StringUtils.removeWhiteSpace(editText.text.toString()))
    }

    // Set unit of quantity
    if (unitStandard.visibility == View.VISIBLE) {
      unitItem.setOnClickListener {
        unitSelected = unitItem.text.toString()
        unitItem.strokeColor = ContextCompat.getColorStateList(context!!, R.color.border_primary)
        unitStandard.strokeColor = ContextCompat.getColorStateList(context!!, R.color.button_border)
        setActionLayoutState()
      }

      unitStandard.setOnClickListener {
        unitSelected = unitStandard.text.toString()
        unitStandard.strokeColor = ContextCompat.getColorStateList(context!!, R.color.border_primary)
        unitItem.strokeColor = ContextCompat.getColorStateList(context!!, R.color.button_border)
        setActionLayoutState()
      }
    }
  }

  /**
   * Setup edit text
   */
  private fun setupField() {
    if (!isAdded) {
      safeDismiss()
      return
    }

    // Set text change listener
    editText.addTextChangedListener(object : Utils.AfterTextChangeWatcher() {
      override fun afterTextChanged(s: Editable) {
        // Set action layout state
        setActionLayoutState(s.toString())
      }
    })

    // Set editor change (IME) listener to dismiss keyboard on 'Done'
    editText.setOnEditorActionListener(TextView.OnEditorActionListener { _, actionID, _ ->
      if (dimensions.isPortrait && actionID == EditorInfo.IME_ACTION_DONE) {
        editItem(StringUtils.removeWhiteSpace(editText.text.toString()))
        return@OnEditorActionListener true
      }
      false
    })

    // Add back button listener
    editText.setOnBackCallback(object : OnBackEditText.OnBackCallback {
      override fun onImeBackPress(onBackEditText: OnBackEditText, text: String) {
        editText.error = null

        // Dismiss with delay after item update
        Handler().postDelayed({ safeDismiss() }, Constants.DELAY_BOTTOM_SHEET)
      }
    })

    // Set item details if saving item
    if (update) {
      val title = if (isQuantity) shoppingItem!!.quantity.toString()
      else shoppingItem!!.note
      try {
        editText.setText(title)
        editText.setSelection(title.length)
      } catch (e: NullPointerException) {
        Utils.e(TAG, "Item name edit text is null", e)
      } catch (f: IndexOutOfBoundsException) {
        Utils.e(TAG, "Set selection out of bounds", f)
      }
    }
  }

  /**
   * Sets the state of the action layout
   */
  private fun setActionLayoutState(text: String = "") {
    if (!isAdded) {
      safeDismiss()
      return
    }
    val isValid = if (isQuantity) (text != shoppingItem!!.quantity.toString() ||
            unitSelected != shoppingItem!!.quantityUnit)
                  else (text != shoppingItem!!.note)
    if (context != null) {
      actionImageView.colorFilter = ThemeUtils.getActiveIconColorFilter(context, isValid)
    }
  }

  /**
   * Bottom Sheet Resumed
   */
  override fun onResume() {
    super.onResume()

    val bottomSheetWidth = dimensions.getBottomSheetWidth(
        resources.getDimension(R.dimen.bottom_sheet_max_width))
    with(scrollView.layoutParams) {
      width = bottomSheetWidth
      height = ViewGroup.LayoutParams.WRAP_CONTENT
      scrollView.layoutParams = this
    }
  }

  /**
   * Bottom Sheet Paused
   */
  override fun onPause() {
    wasChangingConfig = activity != null && activity!!.isChangingConfigurations
    super.onPause()
  }

  /* *********************
   * CONVENIENCE METHODS
   * ******************* */

  /**
   * Edits item's item quantity or note information from string in edit text field
   *
   * @param text quantity or note value
   */
  private fun editItem(text: String) {
    BackgroundExecutor.execute(object : BackgroundExecutor.Task() {
      override fun execute() {

        val isValid = if (isQuantity) (text != shoppingItem!!.quantity.toString() ||
                          unitSelected != shoppingItem!!.quantityUnit)
                      else (text != shoppingItem!!.note)

        if (isValid) {
          // Start modifying item if added to list correctly
          if (shoppingListHandler.modifyingItem(shoppingItem!!.id, true)) {

            // Edit item quantity / note
            if (isQuantity) {
              // Ensure quantity value is valid
              var quantity = 0
              if (text != "") {
                when {
                  text.toInt() > Constants.MAX_QUANTITY -> quantity = Constants.MAX_QUANTITY
                  text.toInt() < Constants.MIN_QUANTITY -> quantity = Constants.MIN_QUANTITY
                  else -> quantity = text.toInt()
                }
              }
              // Change item quantity and unit to values selected by user
              shoppingListHandler.editItemQuantity(shoppingItem!!.id, quantity,
                  Constants.SRC_SHOPPING_LIST)
              shoppingListHandler.editItemQuantityUnit(shoppingItem!!.id, unitSelected,
                  Constants.SRC_SHOPPING_LIST)
            } else shoppingListHandler.editItemNote(shoppingItem!!.id, text)

            // Log shopping list manual item edit title analytic event
          }
          // Modify manual item
          shoppingListHandler.finishedModifyingItem(shoppingItem!!.id)

          // Complete edit manual item on UI thread
          finishModifyItem()
        } else safeDismiss()
      }
    })
  }

  /**
   * Finishes modify (add or edit) manual item on UI thread
   *
   */
  private fun finishModifyItem() {
    UiThreadExecutor.runTask {
      // Post shopping list manual item event

      EventBus.getDefault().post(ShoppingListUpdateItemEvent(shoppingItem!!.id, isExpanded))

      // Item added
      safeDismiss()
    }
  }
}
