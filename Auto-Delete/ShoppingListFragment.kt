/**
 * Class that is used to represent the shopping list Fragment
 * Controls shopping list, add manual item, and search items
 */
class ShoppingListFragment : BaseFragment() {

  /* *********************
   * INTERFACE METHODS
   * ******************* */

  interface ShoppingListFragmentCallback {
    fun massDelete()

    fun searchFromShoppingList(query: String)

    fun shoppingListResult(resultCode: Int, intent: Intent?)
  }

  private lateinit var shoppingListFragmentCallback: ShoppingListFragmentCallback

  /* *********************
   * COMPANION OBJECT
   * ******************* */

  companion object {
    val TAG: String = ShoppingListFragment::class.java.simpleName

    // Auto delete constants
    const val INTERACT = 0
    const val MASS_DELETE = 1

    val itemsToAnimate: HashMap<UUID, Long?> = HashMap()

    fun newInstance(manualItemTitles: Array<String>? = null, isDynamicLink: Boolean? = null)
        : ShoppingListFragment = ShoppingListFragment().apply {
      arguments = Bundle().apply {
        if (!manualItemTitles.isNullOrEmpty()) {
          putStringArray("manualItemTitles", manualItemTitles)
        }
        if (isDynamicLink != null) {
          putBoolean("isDynamicLink", isDynamicLink)
        }
      }
    }

    fun newInstance(sourceID: Int, bookID: Long) = ShoppingListFragment().apply {
      arguments = Bundle().apply {
        putInt("sourceID", sourceID)
        putLong("bookID", bookID)
      }
    }
  }

  /* *********************
   * CLASS VARIABLES
   * ******************* */

  private var isItemDragging: Boolean = false
  private var isModifyDisabled: Boolean = false
  private var isNotHidden: Boolean = false
  private var isSwiping: Boolean = false
  private var isUndo: Boolean = false
  private var swipeExpired: Boolean = false
  private var typeExpired: Boolean = false
  private var wasHeaderDeleted: Boolean = false
  private var manualItemsAdded: Int = 1
  private var shoppingListTouchHelper: ShoppingListTouchHelper? = null

  // Adding/editing manual items
  private var type: Int = ShoppingListEvents.ManualItem.NONE
  private var uuid: UUID? = null
  private var oldPositions: IntArray = intArrayOf() // [0] = Item position, [1] = Header position

  // Swipe to delete
  private var snackbar: Snackbar? = null
  private lateinit var removedHeader: ShoppingListRow
  private lateinit var removedShoppingItem: ShoppingListRow

  // Arguments
  private var sourceID: Int = Constants.SRC_BOOKSHELF
  private var bookID: Long = Book.NO_BOOK_ID
  private var manualItemTitles: Array<String>? = null
  private var isDynamicLink: Boolean = false

  // Instance States
  private var isExportShowing: Boolean = false
  private var isProgressDialogShowing: Boolean = false
  private var removedPosition: Int = Constants.INVALID_POSITION

  /**
   * Injections
   */
  private val dimensions: Dimensions by lazy { Dimensions.getInstance(context) }
  private val picassoUtils: PicassoUtils by lazy { PicassoUtils.getInstance(context) }
  private val jobManager: JobManager by lazy { JobManager_.getInstance_(context) }
  private val shoppingListAdapter: ShoppingListAdapter by lazy { ShoppingListAdapter(context) }
  private val shoppingListHandler: ShoppingListHandler by lazy {
    ShoppingListHandler_.getInstance_(context)
  }
  private val shoppingListModel: ShoppingListModel by lazy {
    ShoppingListModel_.getInstance_(context)
  }
  private val userData: UserData by lazy { UserData.getInstance(context) }

  /**
   * Dimension Res
   */
  private val appBarElevation: Float by lazy { resources.getDimension(R.dimen.default_elevation) }
  private val headerNegativeHeight: Int by lazy {
    resources.getDimension(R.dimen.header_height_negative).toInt()
  }
  private val shoppingListScrollOffset: Float by lazy {
    resources.getDimension(R.dimen.shopping_list_scroll_offset)
  }

  /**
   * OrmLite DAOs
   */
  private var databaseHelper: DatabaseHelper? = null
  private lateinit var bookDao: RuntimeExceptionDao<Book, Long>
  private lateinit var shoppingItemDao: RuntimeExceptionDao<ShoppingItem, Long>
  private lateinit var storeDao: RuntimeExceptionDao<Store, Long>

  /**
   * Views
   */
  private lateinit var appBarLayout: AppBarLayout
  private lateinit var snackbarLayout: CoordinatorLayout
  private lateinit var shoppingListRecyclerView: RecyclerView
  private lateinit var stickyHeaderLayout: ConstraintLayout
  private lateinit var stickyHeaderTextView: MaterialTextView
  private lateinit var emptyLayout: LinearLayout
  private lateinit var emptyImageView: ImageView
  private lateinit var fab: FloatingActionButton

  // Toolbar Menu Items
  private lateinit var toolbarBackButton: ToolbarBackButton
  private lateinit var toolbarSpacer: View
  private lateinit var exportToolbarMenuItem: ToolbarMenuItem
  private lateinit var deleteToolbarMenuItem: ToolbarMenuItem


  /* *********************
   * OVERRIDE METHODS
   * ******************* */

  /**
   * Checks that parent implements interface
   *
   * @param context app context
   */
  override fun onAttach(context: Context) {
    super.onAttach(context)

    if (context is ShoppingListFragmentCallback) {
      shoppingListFragmentCallback = context
    } else {
      throw ClassCastException()
    }
  }

  /**
   * Hidden state of of Fragment has changed
   *
   * @param hidden whether or not the Fragment is hidden
   */
  override fun onHiddenChanged(hidden: Boolean) {
    super.onHiddenChanged(hidden)

    // Track fragment visibility
    isNotHidden = !hidden

    // Reset flags
    resetFlags()

    // Clear pending animations
    itemsToAnimate.clear()

    if (isNotHidden) {
      // Refresh shopping list and log shopping list state event
      refreshShoppingList()
      logShoppingListState()

      // Launch auto delete dialog or snackbar (Main shopping list only)
      if (sourceID == Constants.SRC_BOOKSHELF) {
        launchAutoDeleteSnackbar()
      }
    } else if (snackbar != null && snackbar!!.isShown) {
      // Dismiss Snackbar if shown
      snackbar?.dismiss()
    }
  }

  /**
   * Fragment Created
   *
   * @param args a mapping from String keys to various values
   */
  override fun onCreate(args: Bundle?) {
    // Injections
    injectFragmentArguments()
    inject()

    // Restore state
    restoreSavedInstanceState(args)

    super.onCreate(args)
  }

  /**
   * Inject Fragment Arguments
   */
  private fun injectFragmentArguments() {
    val arguments = arguments
    if (arguments != null) {
      if (arguments.containsKey("sourceID")) {
        sourceID = arguments.getInt("sourceID")
        setHasOptionsMenu(sourceID != Constants.SRC_BOOKSHELF)
      }
      if (arguments.containsKey("bookID")) {
        bookID = arguments.getLong("bookID")
      }
      if (arguments.containsKey("manualItemTitles")) {
        manualItemTitles = arguments.getStringArray("manualItemTitles")
      }
      if (arguments.containsKey("isDynamicLink")) {
        isDynamicLink = arguments.getBoolean("isDynamicLink")
      }

      this.arguments?.clear()
    }
  }

  /**
   * Inject DAOs
   */
  // No need to check cast
  @Suppress("UNCHECKED_CAST")
  private fun inject() {
    try {
      databaseHelper = OpenHelperManager.getHelper(context, DatabaseHelper::class.java)

      bookDao = RuntimeExceptionDao(databaseHelper!!.getDao(Book::class.java) as Dao<Book, Long>)
      shoppingItemDao = RuntimeExceptionDao(
          databaseHelper!!.getDao(ShoppingItem::class.java) as Dao<ShoppingItem, Long>)
      storeDao = RuntimeExceptionDao(databaseHelper!!.getDao(Store::class.java) as Dao<Store, Long>)
    } catch (e: SQLException) {
      Utils.e(TAG, "Could not create DAOs", e)
    }
  }

  /**
   * Restore Fragment state
   */
  private fun restoreSavedInstanceState(args: Bundle?) {
    if (args == null) {
      return
    }
    sourceID = args.getInt("sourceID")
    bookID = args.getLong("bookID")
    isExportShowing = args.getBoolean("isExportShowing")
    isProgressDialogShowing = args.getBoolean("isProgressDialogShowing")
    removedPosition = args.getInt("removedPosition")
  }

  /**
   * Save Fragment state
   *
   * @param args a mapping from String keys to various values
   */
  override fun onSaveInstanceState(args: Bundle) {
    super.onSaveInstanceState(args)
    args.putInt("sourceID", sourceID)
    args.putLong("bookID", bookID)
    args.putBoolean("isExportShowing", isExportShowing)
    args.putBoolean("isProgressDialogShowing", isProgressDialogShowing)
    args.putInt("removedPosition", removedPosition)
  }

  /**
   * Handle Activity results
   *
   * @param requestCode source ID of request
   * @param resultCode  integer result code returned through its setResult()
   * @param data        result data
   */
  override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
    super.onActivityResult(requestCode, resultCode, data)

    when (requestCode) {
      Constants.BOOK_SRC -> {
        // Refresh shopping list
        refreshShoppingList()
      }
    }
  }

  /**
   * Fragment Destroyed
   */
  override fun onDestroy() {
    super.onDestroy()

    // Release adapter database helper
    shoppingListAdapter.releaseDatabase()
    if (shoppingListTouchHelper != null) {
      shoppingListTouchHelper!!.releaseDatabase()
    }

    OpenHelperManager.releaseHelper()
    databaseHelper = null
  }

  /**
   * Fragment Started
   */
  override fun onStart() {
    super.onStart()
    EventBus.getDefault().register(this)
  }

  /**
   * Fragment Stopped
   */
  override fun onStop() {
    EventBus.getDefault().unregister(this)
    super.onStop()
  }

  /**
   * Fragment Resumed
   */
  override fun onResume() {
    super.onResume()

    // Reset flags
    resetFlags()

    // Clear pending animations
    itemsToAnimate.clear()

    // Refresh shopping list
    refreshShoppingList()

    // Set state of export and delete toolbar menu item
    setToolbarIconsUI()
  }

  /**
   * Set state of export toolbar menu item
   */
  private fun setToolbarIconsUI() {
    // Set export visibility
    exportToolbarMenuItem.visibility =
        if (PdfUtils.isPdfPrintAvailable(context) || PdfUtils.isPdfShareAvailable(context)) {
          View.VISIBLE
        } else {
          View.GONE
        }
  }

  /**
   * Set layout resource
   */
  override fun layoutRes() = R.layout.shopping_list

  /**
   * Binds the layout views
   *
   * @param view inflated layout view
   */
  override fun bindViews(view: View) {
    appBarLayout = view.findViewById(R.id.app_bar_layout)
    snackbarLayout = view.findViewById(R.id.snackbar_layout)
    shoppingListRecyclerView = view.findViewById(R.id.shopping_list_recycler_view)
    stickyHeaderLayout = view.findViewById(R.id.sticky_header_layout)
    stickyHeaderTextView = view.findViewById(R.id.sticky_header_text_view)
    emptyLayout = view.findViewById(R.id.empty_layout)
    emptyImageView = view.findViewById(R.id.empty_image_view)
    fab = view.findViewById(R.id.fab)

    // Toolbar Menu Items
    toolbarBackButton = view.findViewById(R.id.toolbar_back_button)
    toolbarSpacer = view.findViewById(R.id.toolbar_spacer)
    exportToolbarMenuItem = view.findViewById(R.id.export_toolbar_menu_item)
    deleteToolbarMenuItem = view.findViewById(R.id.delete_toolbar_menu_item)
  }

  /**
   * Called immediately after the views are bound
   */
  override fun onViewsBound() {
    setupToolbar()
    setupShoppingList()
    setClickListeners()
  }

  /**
   * Setup Toolbar
   */
  private fun setupToolbar() {
    // Set up back button
    toolbarBackButton.visibility = if (sourceID != Constants.SRC_BOOKSHELF) {
      toolbarSpacer.visibility = View.GONE
      View.VISIBLE
    } else {
      toolbarSpacer.visibility = View.VISIBLE
      View.GONE
    }

    // Set header UI
    stickyHeaderLayout.visibility = View.GONE
    stickyHeaderLayout.setBackgroundColor(ThemeUtils.getForegroundColor(context))
  }

  /**
   * Setup shopping list
   */
  private fun setupShoppingList() {
    // Set the shopping list touch HelperCallback and Helper
    val shoppingListTouchHelperCallback =
        ShoppingListTouchHelper.Callback(context!!, shoppingListAdapter)
    val shoppingListTouchHelper = ShoppingListTouchHelper(shoppingListTouchHelperCallback)
    shoppingListTouchHelper.attachToRecyclerView(shoppingListRecyclerView)

    this.shoppingListTouchHelper = shoppingListTouchHelper

    // Adding LinearLayoutManager to RecyclerView
    shoppingListRecyclerView.layoutManager = LinearLayoutManager(context)

    // Optimizing RecyclerView properties
    shoppingListRecyclerView.setHasFixedSize(true)

    // Adding adapter to RecyclerView
    shoppingListRecyclerView.adapter = shoppingListAdapter
    shoppingListAdapter.setSearchEnabled(sourceID == Constants.SRC_BOOKSHELF)
    shoppingListAdapter.notifyDataSetChanged()

    // Set item decorator for equal spacing
    val single = resources.getDimension(R.dimen.single).toInt()
    shoppingListRecyclerView.addItemDecoration(object : RecyclerView.ItemDecoration() {
      override fun getItemOffsets(outRect: Rect, view: View, parent: RecyclerView,
                                  state: RecyclerView.State) {
        super.getItemOffsets(outRect, view, parent, state)
        if (parent.getChildAdapterPosition(view) != 0) {
          outRect.set(0, 0, 0, single)
        }
      }
    })

    // Adding listeners to RecyclerView
    shoppingListRecyclerView.addOnScrollListener(object : RecyclerView.OnScrollListener() {
      override fun onScrolled(recyclerView: RecyclerView, dx: Int, dy: Int) {
        super.onScrolled(recyclerView, dx, dy)
        if (dy != 0) {
          if (shoppingListAdapter.itemCount > 0) {
            // Update sticky header
            updateStickyHeaderUI()
          }
        }

        // Update app bar layout
        updateAppBarLayout()
      }
    })
    shoppingListRecyclerView.addOnChildAttachStateChangeListener(
        object : RecyclerView.OnChildAttachStateChangeListener {
          override fun onChildViewAttachedToWindow(view: View) {
            if (view is ShoppingListManualItemView) {
              view.resetAutoSearchState()
            }
          }

          override fun onChildViewDetachedFromWindow(view: View) {
            if (view is ShoppingListManualItemView) {
              view.resetAutoSearchState()
            }
          }
        })

    // Setup empty image view size
    with(emptyImageView.layoutParams) {
      height = dimensions.backgroundImageSize
      emptyImageView.layoutParams = this
    }
    picassoUtils.picasso
        .load(R.drawable.es_shopping_list)
        .into(emptyImageView)
  }

  /**
   * Deletes an item from the shopping list
   */
  private fun deleteItem() {
    // Clear old view holder positions
    shoppingListRecyclerView.recycledViewPool.clear()

    // Initial variables
    wasHeaderDeleted = false

    // Remove header from list if the last item from this section is removed
    // Has to be done on UI thread or we will get an inconsistency error
    removedShoppingItem = shoppingListAdapter.getShoppingListRow(removedPosition)
    if (shoppingListAdapter.getType(removedPosition - 1) == ShoppingListRow.HEADER
        && (removedPosition == shoppingListAdapter.itemCount - 2
            || shoppingListAdapter.getType(removedPosition + 1) == ShoppingListRow.HEADER
            || shoppingListAdapter.getType(removedPosition + 1) == ShoppingListRow.FOOTER)) {
      wasHeaderDeleted = true
      ShoppingListTouchHelper.headerRemoved = true
      removedHeader = shoppingListAdapter.getShoppingListRow(removedPosition - 1)
      shoppingListAdapter.notifyItemRemoved(removedPosition - 1)
      removedPosition--
    } else {
      ShoppingListTouchHelper.headerRemoved = false
    }

    // Have to remove item from list prior to calling deleteItemAtPosition (Run in background)
    // This needs to be done on the UI thread
    shoppingListAdapter.notifyItemRemoved(removedPosition)

    // Remove header into adapter
    if (wasHeaderDeleted) {
      shoppingListAdapter.removeShoppingListRow(removedPosition)
    }

    // Deletes item or manual item from the shopping list
    shoppingListAdapter.deleteShoppingItem(removedPosition, Constants.SRC_SHOPPING_LIST)

    // Launches Snackbar that allows the user to undo their swipe delete action
    launchUndoSnackbar()
  }

  /**
   * Set click listeners
   */
  private fun setClickListeners() {
    // Set fab click
    fab.setOnClickListener {
      // Reset flags
      resetFlags()

      if (snackbar != null && snackbar!!.isShown) {
        // Dismiss Snackbar if shown
        snackbar?.dismiss()
      }

      showManualItemBottomSheet(null)
    }
  }

  /* *********************
   * SUBSCRIBERS
   * ******************* */

  /**
   * Subscriber to the event bus for [DialogEvents.Dismiss]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onDialogDismissEvent(@Suppress("UNUSED_PARAMETER") event: DialogEvents.Dismiss) {
    isProgressDialogShowing = false
    clearShoppingListRecycling()

    if (isNotHidden && sourceID == Constants.SRC_BOOKSHELF && typeExpired
        && (userData.initAutoDelete() || userData.showAutoDelete())) {
      // Launch auto delete dialog
      userData.setShowAutoDelete(AutoDeleteSetting.SHOW)
      launchAutoDeleteDialog(MASS_DELETE)
      typeExpired = false
    }
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.Delete]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListDeleteEvent(event: ShoppingListEvents.Delete) {
    // Watchdog check for pending actions
    shoppingListHandler.resetActionCount()

    val deleteType = event.deleteType
    if (deleteType == DeleteType.ALL) {
      val tag = ShoppingListDeleteDialog.TAG
      val frag = childFragmentManager.findFragmentByTag(tag)
      if (!ShoppingListDeleteDialog::class.java.isInstance(frag)) {
        // Create Dialog
        val shoppingListDeleteDialog = ShoppingListDeleteDialog()
        try {
          shoppingListDeleteDialog.show(childFragmentManager, tag)
        } catch (e: IllegalStateException) {
          Utils.e(TAG, "Failed to show shopping list delete dialog", e)
        }
      }
    } else {
      performMassDelete(deleteType)
    }
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.DeleteConfirm]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListDeleteConfirmEvent(@Suppress("UNUSED_PARAMETER")
                                       event: ShoppingListEvents.DeleteConfirm) {
    performMassDelete(DeleteType.ALL)
  }

  /**
   * Performs shopping list mass delete
   *
   * @param deleteType type of mass delete
   */
  private fun performMassDelete(deleteType: DeleteType) {
    // Watchdog check for pending actions
    shoppingListHandler.resetActionCount()

    if (shoppingListAdapter.itemCount < 3) {
      // Launches empty shopping list Snackbar
      launchSnackbar(R.string.shopping_list_empty)
      return
    }

    val analyticType: String = when (deleteType) {
      DeleteType.CHECKED -> ShoppingListAnalyticEvents.DELETE_CHECKED
      DeleteType.EXPIRED -> ShoppingListAnalyticEvents.DELETE_EXPIRED
      DeleteType.CHECKED_AND_EXPIRED -> ShoppingListAnalyticEvents.DELETE_CHECKED_AND_EXPIRED
      else -> ShoppingListAnalyticEvents.DELETE_ALL
    }

    // Log shopping list edit delete all analytic event
    EventLoggingService.logEvent(context, ShoppingListAnalyticEvents.shoppingListAnalyticEvent(
        ShoppingListAnalyticEvents.EDIT, ShoppingListAnalyticEvents.DELETE_ACTION)
        .putDelete(analyticType))

    // Delete shopping list items
    if (!isProgressDialogShowing) {
      val tag = ProgressDialog.TAG
      val frag = childFragmentManager.findFragmentByTag(tag)
      if (!ProgressDialog::class.java.isInstance(frag)) {
        // Create args
        val args = Bundle()
        args.putString("message", getString(R.string.deleting_items))

        // Create Dialog
        val progressDialog = ProgressDialog()
        progressDialog.isCancelable = false
        progressDialog.arguments = args
        isProgressDialogShowing = try {
          progressDialog.show(childFragmentManager, tag)

          // Reset flags
          resetFlags()

          // Clear pending animations
          itemsToAnimate.clear()

          // Keep track if deleteType was expired for auto delete
          typeExpired = deleteType == DeleteType.EXPIRED

          // Perform mass delete and notify callback (Only used for shopping list activity)
          shoppingListAdapter.clearShoppingList(deleteType.value, Constants.SRC_SHOPPING_LIST)
          shoppingListFragmentCallback.massDelete()
          true
        } catch (e: IllegalStateException) {
          Utils.e(TAG, "Failed to show progress dialog", e)
          false
        }
      }
    }
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.Export]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListExportEvent(event: ShoppingListEvents.Export) {
    isExportShowing = false
    if (event.exportType == ExportType.CANCEL) {
      // Log export cancel analytic event
      EventLoggingService.logEvent(context, ShoppingListAnalyticEvents.shoppingListAnalyticEvent(
          ShoppingListAnalyticEvents.EXPORT, ShoppingListAnalyticEvents.CANCEL_ACTION))
    } else {
      PdfUtils.onShoppingListExport(context, event.exportType, event.storeCount, event.itemCount,
          event.exportRows, userData)
    }
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.ExportFail]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListExportFailEvent(@Suppress("UNUSED_PARAMETER")
                                    event: ShoppingListEvents.ExportFail) {
    // Launches unknown error occurred Snackbar
    launchSnackbar(R.string.unknown_error_occurred)
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.ExportPrint]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListExportPrintEvent(event: ShoppingListEvents.ExportPrint) {
    if (context != null) {
      val printManager = context!!.getSystemService(Context.PRINT_SERVICE) as PrintManager?
      if (printManager != null) {
        printManager.print(event.title, event.printPDF, null)

        // Log export print analytic event
        logShoppingListExport(ShoppingListAnalyticEvents.PRINT_ACTION, event.storeCount,
            event.itemCount)
        return
      }
    }

    // Launches unknown error occurred Snackbar
    launchSnackbar(R.string.unknown_error_occurred)
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.ExportShare]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListExportShareEvent(event: ShoppingListEvents.ExportShare) {
    // Log export share analytic event
    logShoppingListExport(
        ShoppingListAnalyticEvents.SHARE_ACTION, event.storeCount, event.itemCount)

    val tag = ShareBottomSheet.TAG
    val frag = childFragmentManager.findFragmentByTag(tag)
    if (!ShareBottomSheet::class.java.isInstance(frag)) {
      // Create args
      val args = Bundle()
      args.putBoolean("pdfShare", true)
      args.putString("title", event.title)
      args.putParcelable("intent", event.intent)

      // Create Bottom Sheet
      val shareBottomSheet = ShareBottomSheet()
      shareBottomSheet.arguments = args
      try {
        shareBottomSheet.show(childFragmentManager, tag)
      } catch (e: IllegalStateException) {
        Utils.e(TAG, "Failed to show share bottom sheet", e)
      }
    } else {
      // Launches unknown error occurred Snackbar
      launchSnackbar(R.string.unknown_error_occurred)
    }
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.ItemCheck]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListItemCheckEvent(event: ShoppingListEvents.ItemCheck) {
    // Edit item checked state
    shoppingListAdapter.editShoppingItemCheckedState(event.shoppingItemID, event.isChecked)
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.ItemClick]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListItemClickEvent(event: ShoppingListEvents.ItemClick) {
    val statusID = event.statusID
    val statusTextID = Utils.getStatusTextID(statusID)

    if (statusTextID == Constants.NONE) {
      if (sourceID == Constants.SRC_BOOKSHELF) {
        startBookViewerActivity(event.item)
      } else {
        returnToBookViewerActivity(event.item)
      }
      snackbar?.dismiss()
      return
    }

    if (sourceID == Constants.SRC_BOOKSHELF && statusID == Constants.EXPIRED
        && (userData.initAutoDelete() || userData.showAutoDelete())) {
      // Launch auto delete dialog
      userData.setShowAutoDelete(AutoDeleteSetting.SHOW)
      launchAutoDeleteDialog(INTERACT)
    } else {
      // Launch expired, out of region, or disabled Snackbar
      launchSnackbar(statusTextID)
    }
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.ItemDrag]
   * Updates item check state
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListItemDragEvent(event: ShoppingListEvents.ItemDrag) {
    isItemDragging = event.isDragging
    isModifyDisabled = isItemDragging

    // Update top margin based on dragging state
    with(shoppingListRecyclerView.layoutParams) {
      if (this is FrameLayout.LayoutParams) {
        topMargin = if (isItemDragging) 0 else headerNegativeHeight
      }
      shoppingListRecyclerView.layoutParams = this
    }

    // Update sticky header
    updateStickyHeaderUI()
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.ItemMove]
   *
   * @param event object holding event data
   */
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListItemMoveEvent(event: ShoppingListEvents.ItemMove) {
    removeHeaderIfNeeded(event.originalPosition)

    // Update sticky header
    updateStickyHeaderUI()
  }

  /**
   * Checks the one before the original position of item and remove the row if its
   * type is header and does not have item with the same name.
   *
   * @param originalPos original position of item that was moved from
   */
  private fun removeHeaderIfNeeded(originalPos: Int) {
    val orgRow = shoppingListAdapter.getShoppingListRow(originalPos)

    if (orgRow.type == ShoppingListRow.HEADER) {
      val orgPrevRow = shoppingListAdapter.getShoppingListRow(originalPos - 1)
      val orgNextRow = shoppingListAdapter.getShoppingListRow(originalPos + 1)

      if (orgPrevRow.type == ShoppingListRow.HEADER) {
        shoppingListAdapter.removeShoppingListRow(originalPos - 1)
        shoppingListAdapter.notifyItemRemoved(originalPos - 1)
      } else if (orgNextRow.type == ShoppingListRow.HEADER
          || orgNextRow.type == ShoppingListRow.FOOTER) {
        shoppingListAdapter.removeShoppingListRow(originalPos)
        shoppingListAdapter.notifyItemRemoved(originalPos)
      }
    }
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.ItemSwipe]
   * Updates item check state
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListItemSwipeEvent(event: ShoppingListEvents.ItemSwipe) {
    isSwiping = event.isSwiping

    // Check for empty state if not swiping
    if (!isSwiping) {
      Handler().postDelayed({ showEmptyContainer() }, Constants.DELAY_ACTION)
    }
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.ItemSwipeToDelete]
   * Updates item check state
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListItemSwipeToDeleteEvent(event: ShoppingListEvents.ItemSwipeToDelete) {
    // Track if item swiped was expired
    swipeExpired = event.expired
    if (swipeExpired) {
      userData.setShowAutoDelete(AutoDeleteSetting.SHOW)
    }

    // Delete shopping list item
    removedPosition = event.removedPosition
    deleteItem()
  }

  /**
   * Convenience method for starting a BookViewerActivity
   *
   * @param item object holding item data
   */
  private fun startBookViewerActivity(item: Item) {
    val bookID = item.bookID

    // Fetch book from db
    val book = bookDao.queryForId(bookID)
    if (book != null) {
      // Refresh store
      storeDao.refresh(book.store)

      // Create BookViewerActivity intent
      val intent = BookViewerActivity_.intent(context)
          .mBanner(false)
          .mIsDynamicLink(false)
          .mIsFromItem(true)
          .mShowItemPreview(true)
          .mSourceID(Constants.SRC_SHOPPING_LIST)
          .mBannerPosition(Constants.INVALID_PARAM)
          .mResultRank(Constants.INVALID_PARAM)
          .mPageNumber(null)
          .mItem(item)
          .mSourceItem(item)
          .mBookID(bookID)
          .mItemID(Constants.INVALID_LONG_PARAM)
          .mStoreID(book.store.storeID)
          .get()

      if (intent != null) {
        // Start the BookViewerActivity
        startActivityForResult(intent, Constants.BOOK_SRC)
        return
      }
    }
    bookError(bookID)
  }

  /**
   * Convenience method for returning to an existing BookViewerActivity
   *
   * @param item object holding item data
   */
  private fun returnToBookViewerActivity(item: Item) {
    val bookID = item.bookID

    // Return to existing book viewer
    if (this.bookID == bookID) {
      // Send intent to trigger book viewer update
      val intent = Intent()
          .putExtra("itemNew", true)
          .putExtra("item", item)
          .putExtra("sourceID", Constants.SRC_SHOPPING_LIST)
      shoppingListFragmentCallback.shoppingListResult(Activity.RESULT_OK, intent)
      return
    }

    // Fetch book from db
    val book = bookDao.queryForId(bookID)
    if (book != null) {
      // Send intent to trigger book viewer update
      val intent = Intent()
          .putExtra("bookNew", true)
          .putExtra("bookID", bookID)
          .putExtra("item", item)
          .putExtra("sourceID", Constants.SRC_SHOPPING_LIST)

      // Send shopping list callback result
      shoppingListFragmentCallback.shoppingListResult(Activity.RESULT_OK, intent)
      return
    }
    bookError(bookID)
  }

  /**
   * Book error has occurred
   *
   * @param bookID ID of book
   */
  private fun bookError(bookID: Long) {
    // Log null book
    Utils.w(TAG, "Book with ID $bookID not found")

    // Fetch new books
    jobManager.addJobInBackground(FetchBooksJob())

    // Launches null book Snackbar
    launchSnackbar(R.string.book_null)
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.ManualItemCheck]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListManualItemCheckEvent(event: ShoppingListEvents.ManualItemCheck) {
    // Edit manual item checked state
    shoppingListAdapter.editShoppingItemCheckedState(event.shoppingItemID, event.isChecked)
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.ManualItemClick]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListManualItemClickEvent(event: ShoppingListEvents.ManualItemClick) {
    if (event.search) {
      // Log shopping list search analytic event
      EventLoggingService.logEvent(context, ShoppingListAnalyticEvents.shoppingListAnalyticEvent(
          ShoppingListAnalyticEvents.MANUAL_ITEM, ShoppingListAnalyticEvents.SEARCH_ACTION))

      // Perform search
      shoppingListFragmentCallback.searchFromShoppingList(event.title)
    } else {
      showManualItemBottomSheet(event.uuid)
    }
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.ManualItem]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListManualItemEvent(event: ShoppingListEvents.ManualItem) {
    type = event.type
    uuid = event.uuid
    oldPositions = event.oldPositions
    refreshShoppingList()
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.Sync]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListSyncEvent(event: ShoppingListEvents.Sync) {
    if (!isModifyDisabled && !isSwiping) {
      shoppingListAdapter.refreshResultsInBg(event)
    }

    // Update checked states of shopping items
    UiThreadExecutor.runTask {
      val layoutManager = shoppingListRecyclerView.layoutManager as LinearLayoutManager
      for (i in 0 until shoppingListAdapter.itemCount) {
        when (val view = layoutManager.getChildAt(i)) {
          is ShoppingListItemView -> {
            view.updateCheckedState()
          }
          is ShoppingListManualItemView -> {
            view.updateCheckedState()
          }
        }
      }
    }
  }

  /**
   * Subscriber to the event bus for [ShoppingListEvents.Refresh]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onShoppingListRefresh(event: ShoppingListEvents.Refresh) {
    if (!isModifyDisabled && !isSwiping && !event.undo) {
      clearShoppingListRecycling()
    }

    // Add manual items from app link and set manualItems to null to prevent duplicate add
    addManualItems(manualItemTitles, isDynamicLink)

    if (isProgressDialogShowing) {
      Handler().postDelayed({
        EventBus.getDefault().post(DialogEvents.SafeDismiss())
      }, Constants.DELAY_PROGRESS)
    }

    // Show auto delete snackbar
    if (sourceID != Constants.SRC_BOOKSHELF || isNotHidden) {
      launchAutoDeleteSnackbar()
    }
  }

  /**
   * Subscriber to the event bus for [Events.ToolbarMenuItemClick]
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onToolbarMenuItemClickEvent(event: Events.ToolbarMenuItemClick) {
    @Suppress("NON_EXHAUSTIVE_WHEN")
    when (event.menuItem) {
      ToolbarMenuItem.MenuItem.BACK -> {
        shoppingListFragmentCallback.shoppingListResult(Activity.RESULT_CANCELED, null)
      }
      ToolbarMenuItem.MenuItem.EXPORT -> {
        if (shoppingListAdapter.itemCount < 3) {
          // Launches empty shopping list Snackbar
          launchSnackbar(R.string.shopping_list_empty)
        } else {
          val tag = ExportBottomSheet.TAG
          val frag = childFragmentManager.findFragmentByTag(tag)
          if (!ExportBottomSheet::class.java.isInstance(frag)) {
            // Create Bottom Sheet
            val exportBottomSheet = ExportBottomSheet()
            isExportShowing = try {
              exportBottomSheet.show(childFragmentManager, tag)

              // Set up pdf assets
              PdfUtils.setupShoppingListPdfAssets(context)

              // Log export start analytic event
              EventLoggingService.logEvent(context,
                  ShoppingListAnalyticEvents.shoppingListAnalyticEvent(
                      ShoppingListAnalyticEvents.EXPORT, ShoppingListAnalyticEvents.START_ACTION))
              true
            } catch (e: IllegalStateException) {
              Utils.e(TAG, "Failed to show export dialog", e)
              false
            }
          }
        }
      }
      ToolbarMenuItem.MenuItem.DELETE -> {
        if (shoppingListAdapter.itemCount < 3) {
          // Launches empty shopping list Snackbar
          launchSnackbar(R.string.shopping_list_empty)
        } else {
          val tag = DeleteItemsBottomSheet.TAG
          val frag = childFragmentManager.findFragmentByTag(tag)
          if (!DeleteItemsBottomSheet::class.java.isInstance(frag)) {
            // Create Bottom Sheet
            val deleteItemsBottomSheet = DeleteItemsBottomSheet()
            try {
              deleteItemsBottomSheet.show(childFragmentManager, tag)
            } catch (e: IllegalStateException) {
              Utils.e(TAG, "Failed to show delete items bottom sheet", e)
            }
          }
        }
      }
    }
  }

  /* *********************
   * SNACKBARS
   * ******************* */

  /**
   * Launches a Snackbar
   *
   * @param resourceID ID of the resource
   */
  private fun launchSnackbar(resourceID: Int) {
    // Create Snackbar
    snackbar = Snackbar.make(snackbarLayout, resourceID, Snackbar.LENGTH_SHORT)

    // Prepare Snackbar UI
    prepareSnackbar()
  }

  /**
   * Launches a Snackbar that allows the user to "Undo" a delete
   */
  private fun launchUndoSnackbar() {
    // Create Snackbar
    snackbar = Snackbar.make(snackbarLayout, R.string.item_removed, Snackbar.LENGTH_LONG)
        .setActionTextColor(ContextCompat.getColor(context!!, R.color.snackbar_action))
        .setAction(R.string.undo) {
          isUndo = true

          if (removedPosition >= 2) {
            // Scroll to correct position
            smoothScrollTo(removedPosition)

            // Delay insert item until scroll to item is complete
            Handler().postDelayed({
              insertUndo()
            }, Constants.DELAY_ACTION)
          } else {
            // Insert item
            insertUndo()

            // Delay insert item until scroll to item is complete
            Handler().postDelayed({
              // Scroll to correct position
              smoothScrollTo(removedPosition)
            }, Constants.DELAY_ACTION)
          }
        }

    // Add callback to listen for dismiss event
    snackbar?.addCallback(object : Snackbar.Callback() {
      override fun onDismissed(snackbar: Snackbar?, @DismissEvent event: Int) {
        if (isNotHidden && sourceID == Constants.SRC_BOOKSHELF && swipeExpired) {
          // Launch auto delete dialog
          userData.setShowAutoDelete(AutoDeleteSetting.SHOW)
          launchAutoDeleteDialog(INTERACT)
        }
      }
    })

    // Prepare Snackbar UI
    prepareSnackbar()
  }

  /**
   * Insert item due to "Undo" action
   */
  private fun insertUndo() {
    // Clear old view holder positions
    shoppingListRecyclerView.recycledViewPool.clear()

    if (wasHeaderDeleted) {
      removedPosition++
      // Have to insert item to list prior to calling Undo (Run in background)
      // This needs to be done on the UI thread
      shoppingListAdapter.notifyItemRangeInserted(removedPosition - 1, 2)
    } else {
      // Have to insert item to list prior to calling Undo (Run in background)
      // This needs to be done on the UI thread
      shoppingListAdapter.notifyItemInserted(removedPosition)
    }

    // Insert header into adapter
    if (wasHeaderDeleted) {
      shoppingListAdapter.addShoppingListRow(removedPosition - 1, removedHeader)
    }

    // Insert item/manual item into model
    if (removedShoppingItem.type == ShoppingListRow.ITEM) {
      // Log item undo delete action
      EventLoggingService.logEvent(context,
          ShoppingListAnalyticEvents.shoppingListAnalyticEvent(
              ShoppingListAnalyticEvents.ITEM, ShoppingListAnalyticEvents.UNDO_DELETE_ACTION))

      // Adds item (undo) to the shopping list
      shoppingListAdapter.undoItemInList(removedShoppingItem, removedPosition,
          Constants.SRC_SHOPPING_LIST)
    } else {
      // Log manual item undo delete action
      EventLoggingService.logEvent(context,
          ShoppingListAnalyticEvents.shoppingListAnalyticEvent(
              ShoppingListAnalyticEvents.MANUAL_ITEM,
              ShoppingListAnalyticEvents.UNDO_DELETE_ACTION))

      // Adds manual item (undo) to the shopping liste
      shoppingListAdapter.undoManualItemInList(removedShoppingItem, removedPosition,
          Constants.SRC_SHOPPING_LIST)
    }

    // Check for empty list
    showEmptyContainer()
  }

  /**
   * Launches a Snackbar that informs the user of an auto delete
   */
  private fun launchAutoDeleteSnackbar() {
    val autoDeleteCount = userData.autoDeleteCount
    if (autoDeleteCount > 0L) {
      // Create Snackbar
      snackbar = Snackbar.make(snackbarLayout,
          getString(R.string.auto_delete_snackbar, autoDeleteCount), Snackbar.LENGTH_LONG)
          .setActionTextColor(ContextCompat.getColor(context!!, R.color.snackbar_action))
          .setAction(R.string.settings) {
            // Start MoreActivity
            startActivity(Intent(context, MoreActivity::class.java))
          }

      // Prepare Snackbar UI
      prepareSnackbar(true)

      // Log auto delete analytic event
      EventLoggingService.logEvent(context, AnalyticEvents.analyticEvent(
          AnalyticEvents.AUTO_DELETE, AnalyticEvents.DELETE_ACTION)
          .putItemsDeleted(autoDeleteCount))

      // Resets the auto delete count to 0
      userData.resetAutoDeleteCount()
    }
  }

  /* *********************
   * CONVENIENCE METHODS
   * ******************* */

  /**
   * Add manual items to the shopping list from app link
   *
   * @param manualItemTitles list of manual item titles
   * @param isDynamicLink    whether or not app was opened by dynamic link
   */
  fun addManualItems(manualItemTitles: Array<String>?, isDynamicLink: Boolean) {
    if (!manualItemTitles.isNullOrEmpty()) {
      // Set manualItems to null to prevent duplicate add
      this.manualItemTitles = null

      val validItemTitles = manualItemTitles.filter {
        StringUtils.isValidString(it)
      }

      if (validItemTitles.isNotEmpty()) {
        BackgroundExecutor.execute(object : BackgroundExecutor.Task() {
          override fun execute() {
            // Create manual item
            val manualItems = validItemTitles.map { itemName ->
              itemName.trim().let { ManualItem(it.substring(0, min(it.length, 200))) }
            }

            type = ShoppingListEvents.ManualItem.ADD
            uuid = manualItems.first().manualItemUUID
            manualItemsAdded = validItemTitles.size

            val shoppingItems = manualItems.map {
              // Start manual item modify
              shoppingListHandler.modifyingManualItem(it.manualItemUUID, true)

              // Update items to animate
              itemsToAnimate[it.manualItemUUID!!] = null

              // Create shopping item
              ShoppingItem(it, Store(Constants.MY_LIST_ID, context!!.getString(R.string.my_list)),
                  userData.activeUserGroupID, userData.dateOffset.toDouble())
            }

            // Add manual items
            shoppingListHandler.addManualItems(shoppingItems, Constants.SRC_APP_LINK)

            // Log manual item add analytic event
            EventLoggingService.logEvent(context, AppLinkAnalyticEvents.appLinkAnalyticEvent(
                AppLinkAnalyticEvents.MANUAL_ITEM, AppLinkAnalyticEvents.ADD_ACTION)
                .putItemCount(manualItemsAdded)
                .putType(isDynamicLink))

            EventLoggingService.logEvent(context, AppLinkAnalyticEvents.appLinkAnalyticEvent(
                AppLinkAnalyticEvents.TOTAL).putType(isDynamicLink))

            refreshShoppingList()
          }
        })
      }
    }
  }

  /**
   * Scroll to top of list
   */
  fun scrollToTop() {
    clearShoppingListRecycling()
    smoothScrollTo(0, true)
  }

  /**
   * Prepare snackbar appearance
   *
   * @param autoDelete whether or not we are preparing snackbar for auto delete
   */
  private fun prepareSnackbar(autoDelete: Boolean = false) {
    UiThreadExecutor.runTask {
      // Update font color
      val context = context
      if (context != null) {
        val view = snackbar?.view
        if (view != null) {
          ViewCompat.setElevation(view, 0f)
          view.background.setTint(ContextCompat.getColor(context, R.color.foreground_reverse))
          val textView = view.findViewById<TextView>(com.google.android.material.R.id.snackbar_text)
          if (textView != null) {
            textView.setTextAppearance(R.style.TextAppearance_Small)
            textView.setTextColor(ContextCompat.getColor(context, R.color.text_reverse))
            textView.alpha = 1.0f
            textView.maxLines = if (autoDelete) Int.MAX_VALUE else 1
          }
        }
      }

      // Show Snackbar
      snackbar?.show()
    }
  }

  /**
   * Builds and shows the ManualItemBottomSheet
   */
  private fun showManualItemBottomSheet(uuid: UUID?) {
    val tag = ManualItemBottomSheet.TAG
    val frag = childFragmentManager.findFragmentByTag(tag)
    if (!ManualItemBottomSheet::class.java.isInstance(frag)) {
      // Create Bottom Sheet
      val manualItemBottomSheet = ManualItemBottomSheet()

      if (uuid != null) {
        // Create args
        val args = Bundle()

        // Get shopping item if it exists
        var shoppingItem = shoppingListHandler.getManualItemFromList(uuid)
        if (shoppingItem == null) {
          shoppingItem = shoppingItemDao.queryBuilder()
              .where()
              .eq(ShoppingItem.MANUAL_UUID, uuid)
              .queryForFirst()
        }

        // Cancel action if shopping item or store are null
        if (shoppingItem == null || shoppingItem.store == null) {
          return
        }

        // Refresh store
        storeDao.refresh(shoppingItem.store)

        // Set bottom sheet arguments
        args.putParcelable("shoppingItem", shoppingItem)
        manualItemBottomSheet.arguments = args
      }
      try {
        manualItemBottomSheet.show(childFragmentManager, tag)
      } catch (e: IllegalStateException) {
        Utils.e(TAG, "Failed to show add item bottom sheet", e)
      }
    }
  }

  /**
   * Reset flags
   */
  private fun resetFlags() {
    isItemDragging = false
    isModifyDisabled = false
    isSwiping = false
    isUndo = false
    shoppingListAdapter.resetUndoState()
  }

  /**
   * Refresh shopping list
   */
  fun refreshShoppingList() {
    shoppingListAdapter.refreshResultsInBg()
  }

  /**
   * Clears the shopping list adapter internal stack to make sure there are no corrupt view holders
   */
  private fun clearShoppingListRecycling() {
    UiThreadExecutor.runTask {
      // Clear old view holder positions
      shoppingListRecyclerView.recycledViewPool.clear()

      val uuid = uuid
      if (uuid != null) {
        val positions = shoppingListAdapter.getManualItmPosition(uuid)
        if (positions.isNotEmpty()) {
          when (type) {
            ShoppingListEvents.ManualItem.ADD -> {
              if (positions.size == 2) {
                // Insert header and item
                shoppingListAdapter.notifyItemRangeInserted(positions[1], manualItemsAdded + 1)
              } else {
                // Insert item
                shoppingListAdapter.notifyItemRangeInserted(positions[0], manualItemsAdded)
              }
              manualItemsAdded = 1

              // Scroll to correct position
              smoothScrollTo(positions[0])
            }
            // Item has been edited (title only)
            ShoppingListEvents.ManualItem.EDIT -> {
              shoppingListAdapter.notifyItemChanged(positions[0])
            }
            // Item has been moved (title may have changed as well)
            ShoppingListEvents.ManualItem.MOVE -> {
              shoppingListAdapter.notifyDataSetChanged()

              // Scroll to correct position
              smoothScrollTo(positions[0])
            }
          }
          this.uuid = null
        }
      }
      // Do not want to interrupt animations
      else if (!isUndo && itemsToAnimate.isEmpty()) {
        shoppingListAdapter.notifyDataSetChanged()
      }
      isUndo = false

      // Check for empty list
      showEmptyContainer()
    }
  }

  /**
   * Determine whether or not to show the empty container
   */
  private fun showEmptyContainer() {
    // Set empty shopping list state
    emptyLayout.visibility =
        if (shoppingListAdapter.itemCount > 2) View.GONE else View.VISIBLE

    // Update app bar layout and sticky header
    updateAppBarLayout()
    updateStickyHeaderUI()
  }

  /**
   * Update app bar layout elevation
   */
  private fun updateAppBarLayout() {
    // Set app bar layout elevation
    appBarLayout.elevation = if (shoppingListRecyclerView.computeVerticalScrollOffset() == 0) {
      0f
    } else {
      appBarElevation
    }
  }

  /**
   * Sets the sticky header UI
   */
  private fun updateStickyHeaderUI() {
    if (!isAdded) {
      return
    }

    val layoutManager = shoppingListRecyclerView.layoutManager as LinearLayoutManager
    var position = layoutManager.findFirstVisibleItemPosition()
    if (position < 0 || position >= shoppingListAdapter.itemCount) {
      position = 0
    }
    if (shoppingListAdapter.itemCount > 0) {
      val shoppingListRow = shoppingListAdapter.getShoppingListRow(position)
      val shoppingItem = shoppingListRow.shoppingItem
      val store = shoppingListRow.store
      val type = shoppingListRow.type

      // Get store name
      val storeName = if ((type == ShoppingListRow.ITEM || type == ShoppingListRow.MANUAL_ITEM)
          && shoppingItem != null) shoppingItem.store.getStoreAdapterName(resources)
      else if (type == ShoppingListRow.HEADER && store != null) store.getStoreAdapterName(resources)
      else getString(R.string.my_list)

      // Set sticky header text
      stickyHeaderTextView.text =
          if (StringUtils.isValidStoreName(storeName)) storeName
          else getString(R.string.my_list)
    }

    // Set sticky header layout visibility
    stickyHeaderLayout.visibility =
        if (shoppingListAdapter.itemCount > 2 && !isItemDragging) View.VISIBLE
        else View.GONE
  }

  /**
   * Smooth scroll to position with offset
   *
   * @param position target position
   */
  private fun smoothScrollTo(position: Int) {
    smoothScrollTo(position, false)
  }

  /**
   * Smooth scroll to position with offset
   *
   * @param position target position
   * @param force    whether ot not to force a smooth scroll
   * @return whether or not scrolling was required
   */
  private fun smoothScrollTo(position: Int, force: Boolean) {
    shoppingListRecyclerView.stopScroll()
    val layoutManager = shoppingListRecyclerView.layoutManager as LinearLayoutManager
    val first = layoutManager.findFirstVisibleItemPosition()
    val last = layoutManager.findLastVisibleItemPosition()

    // Do not perform scroll for items that are on screen and near top
    if (!force && position > first + 1 && position <= last - 3) {
      return
    }

    val scrollSpeedScale = if (position < first - 3 || position > last + 3) 1.5f else 0.5f
    val smoothScroller = object : LinearSmoothScroller(context) {
      override fun getVerticalSnapPreference(): Int {
        return SNAP_TO_START
      }

      override fun calculateDyToMakeVisible(view: View, snapPreference: Int): Int {
        return super.calculateDyToMakeVisible(view, snapPreference) +
            (shoppingListScrollOffset * 2.5).toInt()
      }

      override fun calculateSpeedPerPixel(displayMetrics: DisplayMetrics): Float {
        return super.calculateSpeedPerPixel(displayMetrics) / scrollSpeedScale
      }

      override fun onStop() {
        super.onStop()
        shoppingListRecyclerView.run {
          for (i in 0 until childCount) {
            with(getChildAt(i)) {
              if (this is ShoppingListManualItemView) {
                this.performAutoSearch()
              }
            }
          }
        }
      }
    }
    smoothScroller.targetPosition = position
    layoutManager.startSmoothScroll(smoothScroller)
  }

  /* *********************
   * DIALOGS
   * ******************* */

  /**
   * Builds and shows the AutoDeleteDialog
   *
   * @param autoDeleteType what caused the dialog to show
   */
  private fun launchAutoDeleteDialog(autoDeleteType: Int) {
    val tag = AutoDeleteDialog.TAG
    val frag = childFragmentManager.findFragmentByTag(tag)
    if (!AutoDeleteDialog::class.java.isInstance(frag)) {
      // Create args
      val args = Bundle()
      args.putInt("autoDeleteType", autoDeleteType)

      // Create Dialog
      val autoDeleteOptDialog = AutoDeleteDialog()
      autoDeleteOptDialog.arguments = args
      try {
        autoDeleteOptDialog.isCancelable = false
        autoDeleteOptDialog.show(childFragmentManager, tag)

        // Suppress auto delete
        userData.setShowAutoDelete(AutoDeleteSetting.DO_NOT_SHOW)

        // Log auto delete dialog analytic event
        EventLoggingService.logEvent(context, AnalyticEvents.analyticEvent(
            AnalyticEvents.AUTO_DELETE, AnalyticEvents.TYPE_ACTION)
            .putDeleteType(autoDeleteType))
      } catch (e: IllegalStateException) {
        Utils.e(TAG, "Failed to show auth dialog", e)
      }
    }
  }

  /* *********************
   * LOGGING
   * ******************* */

  /**
   * Log shopping list export
   *
   * @param exportType shopping list export type (print or share)
   * @param storeCount number of stores in Pdf
   * @param itemCount  number if items in Pdf
   */
  private fun logShoppingListExport(exportType: String, storeCount: Int, itemCount: Int) {
    EventLoggingService.logEvent(context, ShoppingListAnalyticEvents.shoppingListAnalyticEvent(
        ShoppingListAnalyticEvents.EXPORT, exportType)
        .putStores(storeCount)
        .putItems(itemCount))
  }

  /**
   * Log shopping list state (expired items, total number of items)
   */
  private fun logShoppingListState() {
    if (userData.logShoppingListStateEvent()) {
      // Log shopping list expired state analytic event
      EventLoggingService.logEvent(context,
          ShoppingListAnalyticEvents.shoppingListAnalyticEvent(
              ShoppingListAnalyticEvents.STATE,
              ShoppingListAnalyticEvents.getExpiredItemsRange(shoppingListModel.expiredCount))
              .putItems(shoppingListModel.shoppingItemCount))
    }
  }
}
