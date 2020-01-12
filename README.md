# reebeeProjects
Some samples of my code for projects I worked on during my time as an Android Developer at reebee inc.

## Item Options Files:

### ItemOptionsBottomSheet:
This file creates, sets up and handles the lifecycle for the bottom sheet that is displayed to edit quantity and notes.

### ShoppingListItemView: 
This code initializes the ShoppingListItemView (where quantity and notes are). It handles the click listeners and code for
showing the bottom sheet, expanding and collapsing quantity and notes and saving the user input to our databases. Look for
code with "itemOptions".

### view_shopping_list_item:
This is the XML view for the quantity and notes UI. It contains the entire view for a shopping item, and the quantity and 
notes section is behind it, and programmatically slid down.

## Auto-Delete Files:

### AutoDeleteDialog:
This file creates, sets up and handles the lifecycle for the Auto Delete onboarding dialog. 

### MoreActivity:
This file handles the settings view to toggle turning auto-delete on and off and updating the app's userData 
accordingly. Look for code with "autoDelete".

### ShoppingListFragment:
This file handles ensuring the auto delete dialog gets displayed at a relevant time, and displaying a snackbar to let
the user know expired items were deleted. Look for code with "autoDelete".
