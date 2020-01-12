/**
 * Class that is used to represent the more Activity
 */
class MoreActivity : BaseActivity() {

  /* *********************
   * COMPANION OBJECT
   * ******************* */

  companion object {
    val TAG: String = MoreActivity::class.java.simpleName

    private const val FACEBOOK = 0
    private const val INSTAGRAM = 1
    private const val TWITTER = 2
  }

  /* *********************
   * CLASS VARIABLES
   * ******************* */

  private val chromeTabsUtils: ChromeTabsUtils = ChromeTabsUtils()
  private val autoDeleteHandler: Handler = Handler()
  private var query: String? = ""

  // Instance states
  private var highlight: Boolean = false
  private var settingChange: Boolean = false
  private var date: Date? = null

  /**
   * Injections
   */
  private val jobManager: JobManager by lazy { JobManager_.getInstance_(this) }
  private val userData: UserData by lazy { UserData.getInstance(this) }

  /**
   * OrmLite DAOs
   */
  private var databaseHelper: DatabaseHelper? = null
  private lateinit var suggestionDao: RuntimeExceptionDao<Suggestion, Long>

  /**
   * Views
   */
  private lateinit var toolbar: MaterialTextView
  private lateinit var pushNotificationsSwitch: SwitchMaterial
  private lateinit var sortDefaultTextView: MaterialTextView
  private lateinit var sortAZTextView: MaterialTextView
  private lateinit var layoutGridTextView: MaterialTextView
  private lateinit var layoutListTextView: MaterialTextView
  private lateinit var autoDeleteContainer: ConstraintLayout
  private lateinit var autoDeleteSwitch: SwitchMaterial
  private lateinit var autoDeleteChoices: HorizontalScrollView
  private lateinit var autoDeleteImmediately: MaterialTextView
  private lateinit var autoDeleteSeven: MaterialTextView
  private lateinit var autoDeleteThirty: MaterialTextView
  private lateinit var versionTextView: MaterialTextView

  /* *********************
   * OVERRIDE METHODS
   * ******************* */

  /**
   * Activity Created
   *
   * @param args a mapping from String keys to various values
   */
  override fun onCreate(args: Bundle?) {
    // Injections
    inject()
    injectArguments()

    // Restore state
    restoreSavedInstanceState(args)

    super.onCreate(args)

    // Set layout
    setContentView(R.layout.activity_more)

    // Setup view
    bindViews()
    onViewsBound()

    // Bind the ChromeTabsService
    chromeTabsUtils.bindChromeTabsService(this)
  }

  /**
   * Inject DAOs
   */
  private fun inject() {
    try {
      databaseHelper = OpenHelperManager.getHelper(this, DatabaseHelper::class.java)

      // No need to check cast
      @Suppress("UNCHECKED_CAST")
      suggestionDao = RuntimeExceptionDao(
          databaseHelper!!.getDao(Suggestion::class.java) as Dao<Suggestion, Long>)
    } catch (e: SQLException) {
      Utils.e(TAG, "Could not create DAOs", e)
    }
  }

  /**
   * Inject Arguments
   */
  private fun injectArguments() {
    val intent = intent
    if (intent != null) {
      if (intent.hasExtra("highlight")) {
        highlight = intent.getBooleanExtra("highlight", false)
      }
    }
  }

  /**
   * Restore Activity state
   */
  private fun restoreSavedInstanceState(args: Bundle?) {
    if (args == null) {
      return
    }
    highlight = args.getBoolean("highlight")
    settingChange = args.getBoolean("settingChange")
    date = args.getSerializable("date") as Date
  }

  /**
   * Save Activity state
   *
   * @param args a mapping from String keys to various values
   */
  override fun onSaveInstanceState(args: Bundle) {
    super.onSaveInstanceState(args)
    args.putBoolean("highlight", highlight)
    args.putBoolean("settingChange", settingChange)
    args.putSerializable("date", date)
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
      Constants.FEEDBACK_SRC -> {
        val extras = data?.extras ?: return
        val query = extras.getString("query")
        if (StringUtils.isValidString(query)) {
          this.query = query
          onBackPressed()
        }
      }
    }
  }

  /**
   * Activity Destroyed
   */
  override fun onDestroy() {
    super.onDestroy()

    OpenHelperManager.releaseHelper()
    databaseHelper = null

    // Unbind the ChromeTabsService
    chromeTabsUtils.unbindChromeTabsService(this)
  }

  /**
   * Binds the layout views
   */
  private fun bindViews() {
    toolbar = findViewById(R.id.toolbar)
    pushNotificationsSwitch = findViewById(R.id.push_notifications_switch)
    sortDefaultTextView = findViewById(R.id.sort_default_text_view)
    sortAZTextView = findViewById(R.id.sort_a_to_z_text_view)
    layoutGridTextView = findViewById(R.id.layout_grid_text_view)
    layoutListTextView = findViewById(R.id.layout_list_text_view)
    autoDeleteContainer = findViewById(R.id.auto_delete_container)
    autoDeleteSwitch = findViewById(R.id.auto_delete_switch)
    autoDeleteChoices = findViewById(R.id.auto_delete_choices)
    autoDeleteImmediately = findViewById(R.id.auto_delete_immediately)
    autoDeleteSeven = findViewById(R.id.auto_delete_seven_days)
    autoDeleteThirty = findViewById(R.id.auto_delete_thirty_days)
    versionTextView = findViewById(R.id.version_text_view)
  }

  /**
   * Called immediately after the views are bound
   */
  private fun onViewsBound() {
    setupToolbar()
    setupUI()
  }

  /**
   * Setup Toolbar, and home button
   */
  private fun setupToolbar() {
    toolbar.text = getString(R.string.more)
  }

  /**
   * Setup the Activity UI
   */
  private fun setupUI() {
    // Set push notification UI
    setPushNotificationsUI()
    setUserPreferences()
    setAutoDeleteUI()

    // Set version UI
    versionTextView.text = BuildConfig.VERSION_NAME
  }

  /**
   * Activity Started
   */
  override fun onStart() {
    super.onStart()
    EventBus.getDefault().register(this)
  }

  /**
   * Activity Stopped
   */
  override fun onStop() {
    EventBus.getDefault().unregister(this)
    super.onStop()
  }

  /**
   * Activity Resumed
   */
  override fun onResume() {
    super.onResume()

    // Check for background timeout or account error
    val newDate = Date()
    if (date != null && abs(newDate.time - date!!.time) > Application.appTimeout
        || Application.isAuthAccountError) {
      onBackPressed()
    }
  }

  /**
   * Activity Paused
   */
  override fun onPause() {
    date = Date()
    super.onPause()
  }

  /**
   * Handles on back pressed
   */
  override fun onBackPressed() {
    if (StringUtils.isValidString(query) || settingChange) {
      val intent = Intent()
          .putExtra("query", query)
          .putExtra("settingChange", settingChange)
      setResult(RESULT_OK, intent)
    }

    // Finish Activity safely
    super.onBackPressed()
  }

  /* *********************
   * CLICK LISTENERS (XML)
   * ******************* */

  /**
   * Called when a user updates sort setting
   *
   * @param view selected view
   */
  fun updateSort(view: View) {
    updateSort(if (view.id == R.id.sort_default_text_view) BookshelfSetting.SORT_DEFAULT.value
    else BookshelfSetting.SORT_A_TO_Z.value)

  }

  /**
   * Called when a user updates layout setting
   *
   * @param view selected view
   */
  fun updateLayout(view: View) {
    updateLayout(if (view.id == R.id.layout_grid_text_view) BookshelfSetting.GRID.value
    else BookshelfSetting.LIST.value)
  }

  /**
   * Called when a user selects an auto delete setting
   *
   * @param view selected view
   */
  fun autoDeleteSelected(view: View) {
    val oldAutoDeleteSetting = userData.autoDelete
    when (view.id) {
      R.id.auto_delete_seven_days -> userData.autoDelete = AutoDeleteSetting.SEVEN_DAYS
      R.id.auto_delete_thirty_days -> userData.autoDelete = AutoDeleteSetting.THIRTY_DAYS
      else -> userData.autoDelete = AutoDeleteSetting.IMMEDIATELY
    }

    if (oldAutoDeleteSetting != userData.autoDelete) {
      // Log auto delete analytic event
      EventLoggingService.logEvent(this, MoreAnalyticEvents.moreAnalyticEvent(
          MoreAnalyticEvents.AUTO_DELETE, MoreAnalyticEvents.CHANGE_ACTION)
          .putAutoDeleteSetting(userData.autoDelete))
    }

    // Updates the auto delete UI
    updateAutoDelete(userData.autoDelete)
  }

  /**
   * Called when a user clicks on a cell
   *
   * @param view selected view
   */
  fun cellClick(view: View) {
    when (view.id) {
      R.id.auto_delete_container -> {
        // Clears auto delete highlight
        if (highlight) {
          highlight = false
          autoDeleteHandler.removeCallbacksAndMessages(null)
        }

        // Do not show auto delete onboarding
        userData.setShowAutoDelete(AutoDeleteSetting.DO_NOT_SHOW)

        // Update auto delete setting
        userData.autoDelete = if (userData.autoDelete == AutoDeleteSetting.OFF) {
          AutoDeleteSetting.IMMEDIATELY
        } else {
          AutoDeleteSetting.OFF
        }
        setAutoDeleteUI(true)

        // Log auto delete analytic event
        EventLoggingService.logEvent(this, MoreAnalyticEvents.moreAnalyticEvent(
            MoreAnalyticEvents.AUTO_DELETE, MoreAnalyticEvents.CHANGE_ACTION)
            .putAutoDeleteSetting(userData.autoDelete))
      }
      R.id.push_notifications_container -> {
        userData.setShowPushNotifications()
        userData.setPushNotifications(!userData.pushNotifications())

        // Update push notification UI
        setPushNotificationsUI()

        // Re-register FCM token
        if (userData.pushNotifications()) {
          userData.setPushNotifications(true)
          FirebaseInstanceId.getInstance().instanceId.addOnSuccessListener { instanceIdResult ->
            userData.fcmToken = instanceIdResult.token
            jobManager.addJobInBackground(DeviceUpdateJob())
          }
        } else {
          // Blacklist FCM token
          userData.fcmToken = DeviceUpdateJob.UNREGISTERED_FCM
          jobManager.addJobInBackground(DeviceUpdateJob())
        }

        // Log push notifications analytic event
        EventLoggingService.logEvent(this, MoreAnalyticEvents.moreAnalyticEvent(
            MoreAnalyticEvents.PUSH_NOTIFICATIONS, MoreAnalyticEvents.CHANGE_ACTION)
            .putNotifications(if (userData.pushNotifications()) MoreAnalyticEvents.ALLOWED_ACTION
            else MoreAnalyticEvents.DENIED_ACTION))
      }
      R.id.facebook_container -> {
        startSocialActivity(getString(R.string.facebook_url), FACEBOOK)

        // Log link selected event
        logMoreLinkSelectedEvent(MoreAnalyticEvents.FACEBOOK_ACTION)
      }
      R.id.instagram_container -> {
        startSocialActivity(getString(R.string.instagram_url), INSTAGRAM)

        // Log link selected event
        logMoreLinkSelectedEvent(MoreAnalyticEvents.INSTAGRAM_ACTION)
      }
      R.id.twitter_container -> {
        startSocialActivity(getString(R.string.twitter_url), TWITTER)

        // Log link selected event
        logMoreLinkSelectedEvent(MoreAnalyticEvents.TWITTER_ACTION)
      }
      R.id.blog_container -> {
        startChromeTab(url = getString(R.string.blog_url), title = getString(R.string.blog))

        // Log link selected event
        logMoreLinkSelectedEvent(MoreAnalyticEvents.BLOG_ACTION)
      }
      R.id.careers_container -> {
        startChromeTab(url = getString(R.string.careers_url), title = getString(R.string.careers))

        // Log link selected event
        logMoreLinkSelectedEvent(MoreAnalyticEvents.CAREERS_ACTION)
      }
      else -> {
        // Start the FeedbackActivity
        val intent = Intent(this, FeedbackActivity::class.java)
        startActivityForResult(intent, Constants.FEEDBACK_SRC)

        // Log link selected event
        logMoreLinkSelectedEvent(MoreAnalyticEvents.FEEDBACK_ACTION)
      }
    }
  }

  /* *********************
   * SUBSCRIBERS
   * ******************* */

  /**
   * Subscriber to the event bus for [AuthEvents.AccountError]
   * User's session has been invalidated
   *
   * @param event object holding event data
   */
  // Event subscription method
  @Suppress("unused")
  @Subscribe(threadMode = ThreadMode.POSTING)
  fun onAuthAccountErrorEvent(@Suppress("UNUSED_PARAMETER") event: AuthEvents.AccountError) {
    Application.isAuthAccountError = true
    onBackPressed()
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
        onBackPressed()
      }
    }
  }

  /* *********************
   * CONVENIENCE METHODS
   * ******************* */

  /**
   * Set push notification UI
   */
  private fun setPushNotificationsUI() {
    pushNotificationsSwitch.isChecked = userData.pushNotifications()
  }

  /**
   * Update bookshelf sort
   *
   * @param sort bookshelf sort
   */
  private fun updateSort(sort: Int) {
    // Update sort and UI
    settingChange = true
    userData.setBookshelfSort(sort)
    setUserPreferences()

    // Log bookshelf setting analytic event
    logBookshelfSettingEvent(sort)
  }

  /**
   * Update bookshelf layout
   *
   * @param layout bookshelf layout
   */
  private fun updateLayout(layout: Int) {
    // Update layout and UI
    settingChange = true
    userData.setBookshelfLayout(layout)
    setUserPreferences()

    // Log bookshelf setting analytic event
    logBookshelfSettingEvent(layout)
  }

  /**
   * Update user preferences
   */
  private fun setUserPreferences() {
    // Set sort
    sortDefaultTextView.background = resources.getDrawable(
        if (userData.isBookshelfDefault) {
          R.drawable.toggle_selected
        } else {
          R.drawable.toggle_not_selected
        }, this.theme)
    sortDefaultTextView.setTextColor(
        ThemeUtils.getSecondaryTextColor(this, userData.isBookshelfDefault))

    sortAZTextView.background = resources.getDrawable(
        if (userData.isBookshelfAZ) {
          R.drawable.toggle_selected
        } else {
          R.drawable.toggle_not_selected
        }, this.theme)
    sortAZTextView.setTextColor(ThemeUtils.getSecondaryTextColor(this, userData.isBookshelfAZ))

    // Set layout
    layoutGridTextView.background = resources.getDrawable(
        if (userData.isBookshelfGrid) {
          R.drawable.toggle_selected
        } else {
          R.drawable.toggle_not_selected
        }, this.theme)
    layoutGridTextView.setTextColor(
        ThemeUtils.getSecondaryTextColor(this, userData.isBookshelfGrid))

    layoutListTextView.background = resources.getDrawable(
        if (userData.isBookshelfList) {
          R.drawable.toggle_selected
        } else {
          R.drawable.toggle_not_selected
        }, this.theme)
    layoutListTextView.setTextColor(
        ThemeUtils.getSecondaryTextColor(this, userData.isBookshelfList))
  }

  /**
   * Set auto delete UI
   *
   * @param animate whether or not to animate layout changes
   */
  private fun setAutoDeleteUI(animate: Boolean = false) {
    // Highlight the auto delete cell
    if (highlight) {
      highlightAutoDelete()
    }

    // Update cell UI
    val isChecked = userData.autoDelete != AutoDeleteSetting.OFF
    autoDeleteSwitch.isChecked = isChecked
    if (animate) {
      if (isChecked) {
        expandAutoDelete()
      } else {
        collapseAutoDelete()
      }
    } else {
      with(autoDeleteChoices.layoutParams) {
        height = if (isChecked) ViewGroup.LayoutParams.WRAP_CONTENT else 0
        autoDeleteChoices.layoutParams = this
      }
    }

    if (isChecked) {
      // Updates the auto delete UI
      updateAutoDelete(userData.autoDelete)
    }
  }

  /**
   * Set auto delete settings to highlighted color for 5 seconds then back to normal
   */
  private fun highlightAutoDelete() {
    Handler().postDelayed({
      autoDeleteContainer.isPressed = true
    }, Constants.DELAY_AUTO_DELETE_HIGHLIGHT)

    autoDeleteHandler.postDelayed({
      highlight = false
      autoDeleteContainer.isPressed = false
    }, Constants.AUTO_DELETE_HIGHLIGHT_DURATION)
  }

  /**
   * Expand auto delete delay settings
   */
  private fun expandAutoDelete() {
    // Measure height before expanding
    val widthSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    val heightSpec = View.MeasureSpec.makeMeasureSpec(0, View.MeasureSpec.UNSPECIFIED)
    autoDeleteChoices.measure(widthSpec, heightSpec)

    // Expand view
    val animator: ValueAnimator = slideAnimator(0, autoDeleteChoices.measuredHeight)
    animator.start()
  }

  /**
   * Collapse auto delete delay settings
   */
  private fun collapseAutoDelete() {
    // Get expanded height
    val height = autoDeleteChoices.height

    // Collapse view
    val animator: ValueAnimator = slideAnimator(height, 0)
    animator.start()
  }

  /**
   * Animates sliding animation for auto delete
   *
   * @param start animation value start
   * @param end   animation value end
   */
  private fun slideAnimator(start: Int, end: Int): ValueAnimator {
    val animator: ValueAnimator = ValueAnimator.ofInt(start, end)
    animator.addUpdateListener {
      // update height
      val value = it.animatedValue as Int
      with(autoDeleteChoices.layoutParams) {
        height = value
        autoDeleteChoices.layoutParams = this
      }
    }
    animator.duration = Constants.DELAY_ACTION
    return animator
  }

  /**
   * Updates auto delete choices UI
   *
   * @param autoDelete current auto delete setting
   */
  private fun updateAutoDelete(autoDelete: AutoDeleteSetting) {
    // Set auto delete
    autoDeleteImmediately.background = resources.getDrawable(
        if (autoDelete == AutoDeleteSetting.IMMEDIATELY) {
          R.drawable.toggle_selected
        } else {
          R.drawable.toggle_not_selected
        }, this.theme)
    autoDeleteImmediately.setTextColor(
        ThemeUtils.getSecondaryTextColor(this, autoDelete == AutoDeleteSetting.IMMEDIATELY))

    autoDeleteSeven.background = resources.getDrawable(
        if (autoDelete == AutoDeleteSetting.SEVEN_DAYS) {
          R.drawable.toggle_selected
        } else {
          R.drawable.toggle_not_selected
        }, this.theme)
    autoDeleteSeven.setTextColor(
        ThemeUtils.getSecondaryTextColor(this, autoDelete == AutoDeleteSetting.SEVEN_DAYS))

    autoDeleteThirty.background = resources.getDrawable(
        if (autoDelete == AutoDeleteSetting.THIRTY_DAYS) {
          R.drawable.toggle_selected
        } else {
          R.drawable.toggle_not_selected
        }, this.theme)
    autoDeleteThirty.setTextColor(
        ThemeUtils.getSecondaryTextColor(this, autoDelete == AutoDeleteSetting.THIRTY_DAYS))
  }

  /**
   * Starts a social intent
   *
   * @param socialUrl Url to be opened
   * @param intentID  ID of intent based on drawer category ID (social)
   */
  private fun startSocialActivity(socialUrl: String, intentID: Int) {
    var url = socialUrl
    var title = R.string.app_name
    var intent: Intent? = null
    when (intentID) {
      FACEBOOK -> {
        // Create and start Facebook intent
        intent = Utils.createFacebookIntent(this)
        title = R.string.facebook
      }
      INSTAGRAM -> {
        // Create and start Instagram intent
        intent = Utils.createInstagramIntent(this, url, userData)
        title = R.string.instagram
        url = url.replace("_u/", "")
      }
      TWITTER -> {
        // Create and start Twitter intent
        intent = Utils.createTwitterIntent(this)
        title = R.string.twitter
      }
    }

    // Attempt to start social Activity
    if (intent != null) {
      try {
        startActivity(intent)
        return
      } catch (e: ActivityNotFoundException) {
        Utils.e(TAG, "Failed to start social activity", e)
      } catch (e: SecurityException) {
        Utils.e(TAG, "Failed to start social activity", e)
      }
    }

    // Attempts to start ChromeTab
    startChromeTab(url = url, title = getString(title))
  }

  /**
   * Attempts to start ChromeTab (Will fallback to External Browser, and then WebView)
   *
   * @param url      Url to be loaded
   * @param title    title of WebView
   */
  private fun startChromeTab(url: String, title: String) {
    UiThreadExecutor.runTask {
      // Attempts to start ChromeTab
      if (!chromeTabsUtils.startChromeTab(this, url)) {
        // Try external browser
        try {
          val intent = Intent(Intent.ACTION_VIEW, Uri.parse(Utils.unstuffUrl(url, userData)))
          startActivity(intent)
        } catch (e: ActivityNotFoundException) {
          Utils.e(TAG, "Failed to open default browser", e)

          // Start WebView
          val intent = chromeTabsUtils.createWebView(this, url, title)
          startActivity(intent)
        }
      }
    }
  }

  /* *********************
   * LOGGING
   * ******************* */

  /**
   * Log bookshelf setting event
   *
   * @param bookshelfSetting setting selected by user
   */
  private fun logBookshelfSettingEvent(bookshelfSetting: Int) {
    EventLoggingService.logEvent(this,
        BookshelfAnalyticEvents.bookshelfAnalyticEvent(BookshelfAnalyticEvents.SETTING,
            BookshelfAnalyticEvents.CHANGE_ACTION, BookshelfSetting.get(bookshelfSetting).tag))
  }

  /**
   * Log more link selected event
   *
   * @param link the selected link
   */
  private fun logMoreLinkSelectedEvent(link: String) {
    EventLoggingService.logEvent(this,
        MoreAnalyticEvents.moreAnalyticEvent(MoreAnalyticEvents.LINK, link))
  }
}
