/**
 * This Dialog handles the auto delete interaction
 */
class AutoDeleteDialog : BaseDialog() {

  /* *********************
   * COMPANION OBJECT
   * ******************* */

  companion object {
    val TAG: String = AutoDeleteDialog::class.java.simpleName
  }

  /* *********************
   * CLASS VARIABLES
   * ******************* */

  /**
   * Class variables
   */
  private var autoDeleteType: Int = ShoppingListFragment.INTERACT

  /**
   * Views
   */
  private lateinit var messageTextView: MaterialTextView
  private lateinit var noThanksButton: MaterialButton
  private lateinit var yesButton: MaterialButton

  /* *********************
   * OVERRIDE METHODS
   * ******************* */

  /**
   * Dialog Created
   *
   * @param args a mapping from String keys to various values
   */
  override fun onCreate(args: Bundle?) {
    // Injections
    injectDialogArguments()

    // Restore state
    restoreSavedInstanceState(args)

    super.onCreate(args)
  }

  /**
   * Inject Dialog Arguments
   */
  private fun injectDialogArguments() {
    val args = arguments ?: return
    autoDeleteType = args.getInt("autoDeleteType")
  }

  /**
   * Restore Dialog state
   *
   * @param args a mapping from String keys to various values
   */
  private fun restoreSavedInstanceState(args: Bundle?) {
    if (args == null) {
      return
    }
    autoDeleteType = args.getInt("autoDeleteType")
  }

  /**
   * View Created
   *
   * @param layoutInflater view inflater
   * @param viewGroup      Dialog view container
   * @param args           a mapping from String keys to various values
   * @return the created view
   */
  @Nullable
  override fun onCreateView(layoutInflater: LayoutInflater, @Nullable viewGroup: ViewGroup?,
                            @Nullable args: Bundle?): View? {
    val view = layoutInflater.inflate(R.layout.dialog_auto_delete, viewGroup, false)

    // Get views
    noThanksButton = view.findViewById(R.id.no_thanks_button)
    yesButton = view.findViewById(R.id.yes_button)
    messageTextView = view.findViewById(R.id.message_text_view)

    // Setup view
    setClickListeners()
    setupUI()
    return view
  }

  /**
   * Set click listeners
   */
  private fun setClickListeners() {
    // Set no thanks button click
    noThanksButton.setOnClickListener {
      // Log auto delete selection analytic event

      safeDismiss()
    }

    // Set yes button click
    yesButton.setOnClickListener {

      val intent = Intent(context, MoreActivity::class.java)
      intent.putExtra("highlight", true)
      startActivity(intent)
      safeDismiss()
    }
  }

  /**
   * Setup the Dialog UI
   */
  private fun setupUI() {
    // Set dialog message
    messageTextView.text = getString(when (autoDeleteType) {
      ShoppingListFragment.MASS_DELETE -> R.string.auto_delete_mass_delete_msg
      else -> R.string.auto_delete_interact_msg
    })
  }

  /**
   * Dialog Created
   *
   * @param args a mapping from String keys to various values
   * @return created Dialog
   */
  override fun onCreateDialog(args: Bundle?): Dialog {
    val dialog = super.onCreateDialog(args)
    dialog.requestWindowFeature(Window.FEATURE_NO_TITLE)
    dialog.setCancelable(false)
    dialog.setCanceledOnTouchOutside(false)
    return dialog
  }

  /**
   * Dialog Resumed
   */
  override fun onResume() {
    super.onResume()

    try {
      val window = dialog?.window
      if (window != null) {
        val dialogWidth = resources.getDimension(R.dimen.dialog_width)
        if (dialogWidth < 0) {
          window.setLayout(LayoutParams.MATCH_PARENT, LayoutParams.WRAP_CONTENT)
        } else {
          window.setLayout(dialogWidth.toInt(), LayoutParams.WRAP_CONTENT)
        }
      }
    } catch (e: NullPointerException) {
      Utils.e(TAG, "null auto delete dialog", e)
      safeDismiss()
    }
  }
}
