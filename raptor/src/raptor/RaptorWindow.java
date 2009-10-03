package raptor;

import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.lang.StringUtils;
import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.window.ApplicationWindow;
import org.eclipse.swt.SWT;
import org.eclipse.swt.SWTException;
import org.eclipse.swt.custom.CTabFolder;
import org.eclipse.swt.custom.CTabFolder2Adapter;
import org.eclipse.swt.custom.CTabFolderEvent;
import org.eclipse.swt.custom.CTabItem;
import org.eclipse.swt.custom.SashForm;
import org.eclipse.swt.events.MouseAdapter;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Rectangle;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.CoolBar;
import org.eclipse.swt.widgets.CoolItem;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.ToolBar;
import org.eclipse.swt.widgets.ToolItem;

import raptor.connector.Connector;
import raptor.pref.PreferenceKeys;
import raptor.pref.PreferenceUtil;
import raptor.pref.RaptorPreferenceStore;
import raptor.service.ConnectorService;
import raptor.swt.BrowserWindowItem;
import raptor.swt.ItemChangedListener;
import raptor.swt.SWTUtils;

/**
 * A raptor window is broken up quadrants. Each quadrant is tabbed. You can add
 * a RaptorWindowItem to any quad. Each quad can be maximized and restored. When
 * all of the tabs in a quadrannt are vacant, the area disappears.
 */
public class RaptorWindow extends ApplicationWindow {

	/**
	 * A sash form that loads and stores its weights in a specified key. A
	 * SashForm can only have a RaptorTabFolder or another RaptorSashFolder as a
	 * child.
	 */
	protected class RaptorSashForm extends SashForm {
		protected String key;

		public RaptorSashForm(Composite parent, int style, String key) {
			super(parent, style);
			this.key = key;
		}

		/**
		 * Returns all of the items in the sash that are not in folders that are
		 * minimized.
		 * 
		 * @return
		 */
		public int getItemsInSash() {
			int result = 0;
			for (Control control : getTabList()) {
				if (control instanceof RaptorSashForm) {
					result += ((RaptorSashForm) control).getItemsInSash();
				} else if (control instanceof RaptorTabFolder) {
					RaptorTabFolder folder = (RaptorTabFolder) control;
					result += folder.getMinimized() ? 0 : folder.getItemCount();
				}
			}
			return result;
		}

		public String getKey() {
			return key;
		}

		public void loadFromPreferences() {
			setWeights(getPreferences().getCurrentLayoutSashWeights(key));
		}

		/**
		 * Restores the tab by setting it visibile if it or one of its children
		 * contains items. This has a cascading effect if one of its children is
		 * another RaptorSashForm.
		 */
		public void restore() {
			Control lastChildToShow = null;
			int numberOfChildrenShowing = 0;

			Control[] children = getTabList();

			// First restore tab folders.
			for (int i = 0; i < children.length; i++) {
				if (children[i] instanceof RaptorTabFolder) {
					RaptorTabFolder folder = (RaptorTabFolder) children[i];
					if (folder.getItemCount() > 0 && !folder.getMinimized()) {
						RaptorTabFolder childFolder = ((RaptorTabFolder) children[i]);
						lastChildToShow = childFolder;
						childFolder.setVisible(true);
						childFolder.setMaximized(activeItems.size() == 1);
						childFolder.activate();
						numberOfChildrenShowing++;
					} else {
						children[i].setVisible(false);
					}
				}
			}

			// Now restore sashes.
			for (int i = 0; i < children.length; i++) {
				if (children[i] instanceof RaptorSashForm) {
					if (((RaptorSashForm) children[i]).getItemsInSash() > 0) {
						RaptorSashForm childSashForm = ((RaptorSashForm) children[i]);
						lastChildToShow = childSashForm;
						lastChildToShow.setVisible(true);
						numberOfChildrenShowing++;
					} else {
						if (children[i] != null) {
							children[i].setVisible(false);
						}
					}
				}
			}
			setVisible(numberOfChildrenShowing > 0);
			setMaximizedControl(numberOfChildrenShowing == 1 ? lastChildToShow
					: null);
			if (isVisible()) {
				loadFromPreferences();
			}
		}

		public void storePreferences() {
			getPreferences().setCurrentLayoutSashWeights(key, getWeights());
		}

		@Override
		public String toString() {
			return "RaptorSashForm " + key + " isVisible=" + isVisible()
					+ " maxControl=" + getMaximizedControl();
		}
	}

	/**
	 * A RaptorTabFolder which keeps track of the Quadrant it is in in and its
	 * parent RaptorSashForm. Also provides a raptorMaximize and a raptorRestore
	 * method to make maximizing and restoring easier. A RaptorTabFolder can
	 * only contain RaptorTabItems. A RaptorTabFolder can only have a
	 * RaptorSashForm as its parent. A RaptorTabFolder is also the only
	 * component in its Quadrant. Each Quadrant has a RaptorTabFolder.
	 */
	protected class RaptorTabFolder extends CTabFolder {
		protected Quadrant quad;
		protected RaptorSashForm raptorSash;

		public RaptorTabFolder(RaptorSashForm raptorSash, int style,
				Quadrant quad) {
			super(raptorSash, style);
			this.quad = quad;
			this.raptorSash = raptorSash;
		}

		/**
		 * Activates the current selected item.
		 */
		public void activate() {
			if (getRaptorTabItemSelection() != null) {
				getRaptorTabItemSelection().raptorItem.onActivate();
			}
		}

		@Override
		public void dispose() {
			quad = null;
			raptorSash = null;
			super.dispose();
		}

		/**
		 * Returns the RaptorTabItem at the specified index.
		 */
		public RaptorTabItem getRaptorTabItemAt(int index) {
			return (RaptorTabItem) getItem(index);
		}

		/**
		 * Returns the selected RaptorTabItem null if there are none.
		 */
		public RaptorTabItem getRaptorTabItemSelection() {
			return (RaptorTabItem) getSelection();
		}

		/**
		 * Passivates all of the items in this tab. If includeSelection is true
		 * the current selection is passivated, otherwise its left alone.
		 * 
		 * @param includeSelection
		 */
		public void passivate(boolean includeSelection) {
			for (int i = 0; i < getItemCount(); i++) {
				if (!includeSelection
						&& getRaptorTabItemAt(i) == getRaptorTabItemSelection()) {
				} else {
					getRaptorTabItemAt(i).raptorItem.onPassivate();
				}
			}
		}

		public void passivateActiveateItems() {
			for (int i = 0; i < getItemCount(); i++) {
				if (getMinimized()) {
					getRaptorTabItemAt(i).raptorItem.onPassivate();
				} else if (i == getSelectionIndex()) {
					getRaptorTabItemAt(i).raptorItem.onActivate();
				} else {
					getRaptorTabItemAt(i).raptorItem.onPassivate();
				}
			}
		}

		/**
		 * Maximizes this folder in the window.
		 */
		public void raptorMaximize() {

			// First hide all folders besides ourself.
			for (RaptorTabFolder folder : folders) {
				if (folder != this) {
					folder.setVisible(false);
					folder.passivate(true);
					folder.setMaximized(false);
				}
			}
			setVisible(true);

			for (RaptorSashForm form : sashes) {
				if (form == raptorSash) {
					form.setVisible(true);
					form.setMaximizedControl(this);
				} else {
					form.setVisible(false);
					form.setMaximizedControl(null);
				}
			}

			List<RaptorSashForm> parents = new ArrayList<RaptorSashForm>(10);
			Control currentSashParent = raptorSash;

			// Build a list of all the parents. The last entry in the list will
			// be the greatest ancestor.
			while (currentSashParent instanceof RaptorSashForm) {
				parents.add((RaptorSashForm) currentSashParent);
				currentSashParent = currentSashParent.getParent();
			}

			// Now walk the list backwards maximizing the sash forms to the
			// child added.
			for (int i = 0; i < parents.size() - 1; i++) {
				parents.get(i + 1).setVisible(true);
				parents.get(i + 1).setMaximizedControl(parents.get(i));
			}
			this.setMaximized(true);
		}

		/**
		 * Override to make sure setSelection(int index) gets invoked.
		 */
		@Override
		public void setSelection(CTabItem item) {
			for (int i = 0; i < getItemCount(); i++) {
				if (getItem(i) == item) {
					setSelection(i);
				}
			}
		}

		/**
		 * Overridden to add the toolbar to the CTabFolder.
		 */
		@Override
		public void setSelection(int index) {
			super.setSelection(index);
			passivateActiveateItems();
			updateToolbar();
		}

		@Override
		public void setVisible(boolean isVisible) {
			super.setVisible(isVisible);
		}

		@Override
		public String toString() {
			return "RaptorTabFolder " + quad + " isVisible=" + isVisible()
					+ " itemCount=" + getItemCount() + " currentSelection="
					+ getRaptorTabItemSelection();
		}

		public void updateToolbar() {
			RaptorTabItem currentSelection = (RaptorTabItem) getSelection();
			Control existingControl = getTopRight();
			if (existingControl != null) {
				existingControl.setVisible(false);
				setTopRight(null);
			}
			if (currentSelection != null) {
				Control newControl = currentSelection.raptorItem
						.getToolbar(this);
				if (newControl != null) {
					newControl.setVisible(true);
					setTopRight(newControl, SWT.RIGHT);
					setTabHeight(Math.max(newControl.computeSize(SWT.DEFAULT,
							SWT.DEFAULT).y, getTabHeight()));
				}
			}
		}
	}

	/**
	 * A RaptorTabItem. All items added to RaptorTabFoldrs should be
	 * RaptorTabItems RaptorTabItems can only contain RaptorWindowItem controls.
	 */
	protected class RaptorTabItem extends CTabItem {
		protected RaptorWindowItem raptorItem;
		protected ItemChangedListener listener;
		protected RaptorTabFolder raptorParent;
		protected boolean disposed = false;

		public RaptorTabItem(RaptorTabFolder parent, int style,
				RaptorWindowItem item) {
			this(parent, style, item, true);
		}

		public RaptorTabItem(RaptorTabFolder parent, int style,
				final RaptorWindowItem item, boolean isInitingItem) {
			super(parent, style);
			raptorParent = parent;

			if (LOG.isDebugEnabled()) {
				LOG.debug("Creating RaptorTabItem.");
			}

			raptorItem = item;
			if (isInitingItem) {
				if (LOG.isDebugEnabled()) {
					LOG.debug("initing item.");
				}
				item.init(parent);
			}

			item.addItemChangedListener(listener = new ItemChangedListener() {
				public void itemStateChanged() {
					if (!disposed && item.getControl() != null
							&& !item.getControl().isDisposed()) {
						RaptorWindow.this.getShell().getDisplay().asyncExec(
								new Runnable() {
									public void run() {

										if (LOG.isDebugEnabled()) {
											LOG
													.debug("Item changed, updating text,title,showClose");
										}
										try {
											setText(item.getTitle());
											setImage(item.getImage());
											setShowClose(true);
											if (raptorParent.getSelection() == RaptorTabItem.this) {
												raptorParent.updateToolbar();
											}
										} catch (SWTException swt) {
											// Just eat it. It is probably a
											// widget is
											// disposed exception
											// and i can't figure out how to
											// avoid it.
										}
									}
								});
					}
				}
			});

			setControl(item.getControl());
			setText(item.getTitle());
			setImage(item.getImage());
			setShowClose(true);
			parent.layout(true, true);
			parent.setSelection(this);
			activeItems.add(this);
		}

		@Override
		public void dispose() {
			raptorItem = null;
			raptorParent = null;
			super.dispose();
		}

		public void onMoveTo(RaptorTabFolder newParent) {
			if (!raptorItem.getControl().isReparentable()) {
				Raptor
						.getInstance()
						.alert(
								"You can only move between quadrants if reparenting is supported in your SWT environment.");
			} else {
				raptorItem.removeItemChangedListener(listener);
				setControl(null);
				raptorItem.getControl().setParent(newParent);
				activeItems.remove(this);
				new RaptorTabItem(newParent, getStyle(), raptorItem, false);
				dispose();
				restoreFolders();
			}
		}

		@Override
		public String toString() {
			return "RaptorTabItem: " + getText() + " isVisible="
					+ getControl().isVisible();
		}
	}

	Log LOG = LogFactory.getLog(RaptorWindow.class);

	protected RaptorTabFolder[] folders = new RaptorTabFolder[Quadrant.values().length];
	protected RaptorSashForm[] sashes = new RaptorSashForm[6];

	protected RaptorSashForm quad1quad234567quad8Sash;
	protected RaptorSashForm quad2quad34567Sash;
	protected RaptorSashForm quad3quad4Sash;
	protected RaptorSashForm quad34quad567Sash;
	protected RaptorSashForm quad56quad7Sash;
	protected RaptorSashForm quad5quad6Sash;

	protected CoolBar topCoolbar;
	protected CoolBar leftCoolbar;
	protected ToolBar foldersMinimizedToolbar;
	protected CoolItem foldersMinimiziedCoolItem;

	protected Composite windowComposite;
	protected Composite statusBar;
	protected Label statusLabel;
	protected Map<String, Label> pingLabelsMap = new HashMap<String, Label>();
	protected List<RaptorTabItem> activeItems = Collections
			.synchronizedList(new ArrayList<RaptorTabItem>());

	public RaptorWindow() {
		super(null);
		addMenuBar();
	}

	/**
	 * Adds a RaptorWindowItem to the RaptorWindow.
	 */
	public void addRaptorWindowItem(final RaptorWindowItem item) {
		addRaptorWindowItem(item, true);
	}

	/**
	 * Adds a RaptorWindowItem to the RaptorWindow. If isAsynch the window item
	 * is added asynchronously, otherwise it is added synchronously.
	 */
	public void addRaptorWindowItem(final RaptorWindowItem item,
			boolean isAsynch) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Adding raptor window item " + item);
		}

		if (!isAsynch) {
			getShell().getDisplay().syncExec(new Runnable() {
				public void run() {
					RaptorTabFolder folder = getTabFolder(item
							.getPreferredQuadrant());
					new RaptorTabItem(folder, SWT.NONE, item);
					restoreFolders();
				}
			});
		} else {
			getShell().getDisplay().asyncExec(new Runnable() {
				public void run() {
					RaptorTabFolder folder = getTabFolder(item
							.getPreferredQuadrant());
					new RaptorTabItem(folder, SWT.NONE, item);
					restoreFolders();
				}
			});

		}
	}

	/**
	 * Adjusts the left coolbar for quadrants minimized.
	 */
	protected void adjustToFoldersItemsMinimizied() {
		ToolItem[] folderMinItems = foldersMinimizedToolbar.getItems();
		for (ToolItem item : folderMinItems) {
			item.dispose();
		}

		boolean isAFolderMinimized = false;
		for (int i = 0; i < folders.length; i++) {
			if (folders[i].getMinimized()) {
				isAFolderMinimized = true;
				break;
			}
		}

		if (isAFolderMinimized) {
			System.err.println("A folder was minimized.");

			for (Quadrant quadrant : Quadrant.values()) {
				if (getTabFolder(quadrant).getMinimized()) {
					ToolItem item = new ToolItem(foldersMinimizedToolbar,
							SWT.PUSH);
					item.setText(quadrant.name());
					item.setToolTipText("Restore quadrant " + quadrant.name());
					final Quadrant finalQuad = quadrant;
					item.addSelectionListener(new SelectionAdapter() {
						@Override
						public void widgetSelected(SelectionEvent e) {
							getTabFolder(finalQuad).setMinimized(false);
							restoreFolders();
						}
					});

					System.err.println("Adeed tool item for quad: " + quadrant);
				}
			}
		}

		foldersMinimizedToolbar.pack();
		foldersMinimiziedCoolItem.setPreferredSize(foldersMinimizedToolbar
				.computeSize(SWT.DEFAULT, SWT.DEFAULT));

		if (!isAFolderMinimized) {
			leftCoolbar.setVisible(false);
		} else {
			leftCoolbar.setVisible(true);
		}

		leftCoolbar.layout(true);
		windowComposite.layout(true);
	}

	/**
	 * Returns the number of items in the specified quad.
	 */
	public int countItems(Quadrant... quads) {
		int count = 0;
		for (Quadrant quad : quads) {
			count += getTabFolder(quad).getItemCount();
		}
		return count;
	}

	/**
	 * Creates the controls.
	 */
	@Override
	protected Control createContents(Composite parent) {
		getShell().addListener(SWT.Close, new Listener() {
			public void handleEvent(Event e) {
				Raptor.getInstance().shutdown();
			}
		});

		getShell().setText(
				Raptor.getInstance().getPreferences().getString(
						PreferenceKeys.APP_NAME));
		getShell().setImage(
				Raptor.getInstance().getImage(
						"resources/common/images/raptorIcon.gif"));

		parent.setLayout(SWTUtils.createMarginlessGridLayout(1, true));

		windowComposite = new Composite(parent, SWT.NONE);
		windowComposite.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true,
				true));
		windowComposite
				.setLayout(SWTUtils.createMarginlessGridLayout(2, false));

		createTopCoolbar();
		createLeftCoolbar();
		createFolderAndSashControls();
		createStatusBarControls();
		return windowComposite;
	}

	/**
	 * Creates just the folder,sash,and quad composite controls. Initially they
	 * all start out not visible and noy maximized.
	 */
	protected void createFolderAndSashControls() {
		createQuad1Quad234567QuadControls();

		for (int i = 0; i < folders.length; i++) {
			initQuadrantFolder(folders[i]);
		}

		sashes[0] = quad1quad234567quad8Sash;
		sashes[1] = quad2quad34567Sash;
		sashes[2] = quad34quad567Sash;
		sashes[3] = quad3quad4Sash;
		sashes[4] = quad56quad7Sash;
		sashes[5] = quad5quad6Sash;

		for (int i = 0; i < sashes.length; i++) {
			sashes[i].loadFromPreferences();
			sashes[i].setVisible(false);
			sashes[i].setMaximizedControl(null);
		}
	}

	protected void createLeftCoolbar() {
		leftCoolbar = new CoolBar(windowComposite, SWT.FLAT | SWT.VERTICAL);
		leftCoolbar.setLayoutData(new GridData(SWT.LEFT, SWT.FILL, false, true,
				1, 1));
		foldersMinimizedToolbar = new ToolBar(leftCoolbar, SWT.FLAT
				| SWT.VERTICAL);

		foldersMinimiziedCoolItem = new CoolItem(leftCoolbar, SWT.NONE);
		foldersMinimiziedCoolItem.setControl(foldersMinimizedToolbar);
		foldersMinimiziedCoolItem.setPreferredSize(foldersMinimizedToolbar
				.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}

	/**
	 * Creates the menu items.
	 */
	@Override
	protected MenuManager createMenuManager() {
		MenuManager menuBar = new MenuManager("Raptor");
		MenuManager configureMenu = new MenuManager("&Configure");
		MenuManager helpMenu = new MenuManager("&Help");

		Connector[] connectors = ConnectorService.getInstance().getConnectors();

		for (Connector connector : connectors) {
			MenuManager manager = connector.getMenuManager();
			if (manager != null) {
				menuBar.add(manager);
			}
		}

		configureMenu.add(new Action("Preferences") {
			@Override
			public void run() {
				PreferenceUtil.launchPreferenceDialog();
			}
		});
		helpMenu.add(new Action("&About") {
			@Override
			public void run() {
				Raptor.getInstance().alert("Comming soon.");
			}
		});
		helpMenu.add(new Action("&Fics Commands Help") {
			@Override
			public void run() {
				Raptor
						.getInstance()
						.getRaptorWindow()
						.addRaptorWindowItem(
								new BrowserWindowItem(
										"Fics Commands Help",
										Raptor
												.getInstance()
												.getPreferences()
												.getString(
														PreferenceKeys.FICS_COMMANDS_HELP_URL)));
			}
		});

		menuBar.add(configureMenu);
		menuBar.add(helpMenu);
		return menuBar;
	}

	/**
	 * Creates the sash hierarchy.
	 */
	protected void createQuad1Quad234567QuadControls() {
		quad1quad234567quad8Sash = new RaptorSashForm(windowComposite,
				SWT.VERTICAL | SWT.SMOOTH,
				PreferenceKeys.QUAD1_QUAD234567_QUAD8_SASH_WEIGHTS);
		quad1quad234567quad8Sash.setLayoutData(new GridData(SWT.FILL, SWT.FILL,
				true, true, 1, 1));

		folders[Quadrant.I.ordinal()] = new RaptorTabFolder(
				quad1quad234567quad8Sash, SWT.BORDER, Quadrant.I);

		quad2quad34567Sash = new RaptorSashForm(quad1quad234567quad8Sash,
				SWT.HORIZONTAL | SWT.SMOOTH,
				PreferenceKeys.QUAD2_QUAD234567_SASH_WEIGHTS);

		folders[Quadrant.VIII.ordinal()] = new RaptorTabFolder(
				quad1quad234567quad8Sash, SWT.BORDER, Quadrant.VIII);

		folders[Quadrant.II.ordinal()] = new RaptorTabFolder(
				quad2quad34567Sash, SWT.BORDER, Quadrant.II);

		quad34quad567Sash = new RaptorSashForm(quad2quad34567Sash, SWT.VERTICAL
				| SWT.SMOOTH, PreferenceKeys.QUAD34_QUAD567_SASH_WEIGHTS);

		quad3quad4Sash = new RaptorSashForm(quad34quad567Sash, SWT.HORIZONTAL
				| SWT.SMOOTH, PreferenceKeys.QUAD3_QUAD4_SASH_WEIGHTS);

		quad56quad7Sash = new RaptorSashForm(quad34quad567Sash, SWT.HORIZONTAL
				| SWT.SMOOTH, PreferenceKeys.QUAD56_QUAD7_SASH_WEIGHTS);

		folders[Quadrant.III.ordinal()] = new RaptorTabFolder(quad3quad4Sash,
				SWT.BORDER, Quadrant.III);

		folders[Quadrant.IV.ordinal()] = new RaptorTabFolder(quad3quad4Sash,
				SWT.BORDER, Quadrant.IV);

		quad5quad6Sash = new RaptorSashForm(quad56quad7Sash, SWT.VERTICAL
				| SWT.SMOOTH, PreferenceKeys.QUAD5_QUAD6_SASH_WEIGHTS);

		folders[Quadrant.V.ordinal()] = new RaptorTabFolder(quad5quad6Sash,
				SWT.BORDER, Quadrant.V);

		folders[Quadrant.VI.ordinal()] = new RaptorTabFolder(quad5quad6Sash,
				SWT.BORDER, Quadrant.VI);

		folders[Quadrant.VII.ordinal()] = new RaptorTabFolder(quad56quad7Sash,
				SWT.BORDER, Quadrant.VII);
	}

	/**
	 * Creates the status bar controls.
	 */
	protected void createStatusBarControls() {
		statusBar = new Composite(windowComposite, SWT.NONE);
		statusBar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true, false,
				2, 1));
		GridLayout layout = new GridLayout(10, false);
		layout.marginTop = 0;
		layout.marginBottom = 0;
		layout.marginHeight = 0;
		statusBar.setLayout(layout);

		statusLabel = new Label(statusBar, SWT.NONE);
		GridData gridData = new GridData();
		gridData.grabExcessHorizontalSpace = true;
		gridData.grabExcessVerticalSpace = false;
		gridData.horizontalAlignment = SWT.FILL;
		gridData.verticalAlignment = SWT.CENTER;
		statusLabel.setLayoutData(gridData);
		statusLabel.setFont(Raptor.getInstance().getPreferences().getFont(
				PreferenceKeys.APP_STATUS_BAR_FONT));
		statusLabel.setForeground(Raptor.getInstance().getPreferences()
				.getColor(PreferenceKeys.APP_STATUS_BAR_COLOR));
	}

	protected void createTopCoolbar() {
		topCoolbar = new CoolBar(windowComposite, SWT.FLAT);
		topCoolbar.setLayoutData(new GridData(SWT.FILL, SWT.CENTER, true,
				false, 2, 1));

		ToolBar toolBar = new ToolBar(topCoolbar, SWT.FLAT);
		ToolItem toolItem = new ToolItem(toolBar, SWT.PUSH);
		toolItem.setText("TestTopCoolbar");
		toolItem.addSelectionListener(new SelectionAdapter() {

			@Override
			public void widgetSelected(SelectionEvent e) {
				Raptor
						.getInstance()
						.alert(
								"This is just to test out what a top toolbar would "
										+ "look like. I am thinking about adding the decaf toolbar "
										+ "like buttons up here");
			}

		});
		toolBar.pack();

		CoolItem coolItem = new CoolItem(topCoolbar, SWT.NONE);
		coolItem.setControl(toolBar);
		coolItem
				.setPreferredSize(toolBar.computeSize(SWT.DEFAULT, SWT.DEFAULT));
	}

	/**
	 * Disposes all the resources that will not be cleaned up when this window
	 * is closed.
	 */
	public void dispose() {
		if (pingLabelsMap != null) {
			pingLabelsMap = null;
		}
		if (activeItems != null) {
			activeItems.clear();
			activeItems = null;
		}
		if (folders != null) {
			folders = null;
		}
		if (sashes != null) {
			sashes = null;
		}
	}

	/**
	 * Disposes an item and removes it from the RaptorWindow. After this method
	 * is invoked the item passed in is disposed.
	 */
	public void disposeRaptorWindowItem(final RaptorWindowItem item) {
		getShell().getDisplay().syncExec(new Runnable() {
			public void run() {
				RaptorTabItem tabItem = null;
				synchronized (activeItems) {
					for (RaptorTabItem currentTabItem : activeItems) {
						if (currentTabItem.raptorItem == item) {
							tabItem = currentTabItem;
							break;
						}
					}
				}
				if (item != null) {
					tabItem.dispose();
					item.dispose();
					restoreFolders();
				}
			}
		});
	}

	protected RaptorPreferenceStore getPreferences() {
		return Raptor.getInstance().getPreferences();
	}

	/**
	 * Returns the quad the current window item is in. Null if the windowItem is
	 * not being managed.
	 */
	public Quadrant getQuadrant(RaptorWindowItem windowItem) {
		Quadrant result = null;
		synchronized (activeItems) {
			for (RaptorTabItem currentTabItem : activeItems) {
				if (currentTabItem.raptorItem == windowItem) {
					result = currentTabItem.raptorParent.quad;
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Returns an array of all RaptorWindowItems currently active.
	 */
	public RaptorWindowItem[] getRaptorWindowItems() {
		return activeItems.toArray(new RaptorWindowItem[0]);
	}

	/**
	 * Returns the RaptorTabFolder at the specified 0 based index.
	 */
	public RaptorTabFolder getTabFolder(int index) {
		return folders[index];
	}

	/**
	 * Returns the RaptorTabFolder at the quad.
	 */
	public RaptorTabFolder getTabFolder(Quadrant quad) {
		return folders[quad.ordinal()];
	}

	/**
	 * Initialzes the windows bounds.
	 */
	@Override
	protected void initializeBounds() {
		Rectangle screenBounds = getPreferences().getCurrentLayoutRectangle(
				PreferenceKeys.WINDOW_BOUNDS);

		if (screenBounds.width == -1 || screenBounds.height == -1) {
			Rectangle fullViewBounds = Display.getCurrent().getPrimaryMonitor()
					.getBounds();
			screenBounds.width = fullViewBounds.width;
			screenBounds.height = fullViewBounds.height;
		}
		getShell().setSize(screenBounds.width, screenBounds.height);
		getShell().setLocation(screenBounds.x, screenBounds.y);
	}

	/**
	 * Initializes a quad folder. Also sets up all of the listeners on the
	 * folder.
	 * 
	 * @param folder
	 */
	protected void initQuadrantFolder(final RaptorTabFolder folder) {
		folder.setSimple(false);
		folder.setUnselectedImageVisible(false);
		folder.setUnselectedCloseVisible(false);
		folder.setMaximizeVisible(true);
		folder.setMinimizeVisible(true);

		folder.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e) {
				RaptorTabItem selection = folder.getRaptorTabItemSelection();
				selection.raptorItem.onActivate();
				folder.setTopRight(selection.raptorItem.getToolbar(folder),
						SWT.RIGHT);
			}
		});

		folder.addCTabFolder2Listener(new CTabFolder2Adapter() {
			@Override
			public void close(CTabFolderEvent event) {
				RaptorTabItem item = (RaptorTabItem) folder.getSelection();
				if (item.raptorItem.confirmClose()) {
					event.doit = true;
					// item.raptorItem.dispose();
					// item.dispose();
					getShell().getDisplay().asyncExec(new Runnable() {
						public void run() {
							restoreFolders();
						}
					});
				} else {
					event.doit = false;
				}
			}

			@Override
			public void maximize(CTabFolderEvent event) {
				folder.setMaximized(true);
				folder.raptorMaximize();
			}

			@Override
			public void minimize(CTabFolderEvent event) {
				// folder.getRaptorTabItemSelection().raptorItem.onPassivate();
				folder.setMinimized(true);
				restoreFolders();
			}

			@Override
			public void restore(CTabFolderEvent event) {
				restoreFolders();

				folder.getDisplay().timerExec(100, new Runnable() {
					public void run() {
						folder.getRaptorTabItemSelection().raptorItem
								.onActivate();
					}
				});
			}
		});

		folder.addMouseListener(new MouseAdapter() {
			@Override
			public void mouseDoubleClick(MouseEvent e) {
				if (e.count == 2) {
					if (folder.getMaximized()) {
						restoreFolders();
					} else {
						folder.raptorMaximize();
					}
				}
			}

			@Override
			public void mouseDown(MouseEvent e) {
				if (e.button == 3) {
					Menu menu = new Menu(folder.getShell(), SWT.POP_UP);

					if (folder.getItemCount() > 0) {
						MenuItem item = new MenuItem(menu, SWT.PUSH);
						item.setText("Close");
						item.addListener(SWT.Selection, new Listener() {
							public void handleEvent(Event e) {
								if (folder.getRaptorTabItemSelection().raptorItem
										.confirmClose()) {
									folder.getRaptorTabItemSelection()
											.dispose();
								}
								if (folder.getItemCount() == 0) {
									restoreFolders();
								} else {
									folder.setSelection(folder
											.getSelectionIndex());
								}
							}
						});
					}
					if (folder.getItemCount() > 1) {
						MenuItem item = new MenuItem(menu, SWT.PUSH);
						item.setText("Close Others");
						item.addListener(SWT.Selection, new Listener() {
							public void handleEvent(Event e) {
								List<RaptorTabItem> itemsToClose = new ArrayList<RaptorTabItem>(
										folder.getItemCount());
								for (int i = 0; i < folder.getItemCount(); i++) {
									if (i != folder.getSelectionIndex()
											&& folder.getRaptorTabItemAt(i).raptorItem
													.confirmClose()) {
										itemsToClose.add(folder
												.getRaptorTabItemAt(i));
									}
								}
								for (RaptorTabItem item : itemsToClose) {
									item.dispose();
								}
								folder.setSelection(folder.getSelectionIndex());
							}
						});
					}
					if (folder.getItemCount() > 0) {
						MenuItem item = new MenuItem(menu, SWT.PUSH);
						item.setText("Close All");
						item.addListener(SWT.Selection, new Listener() {
							public void handleEvent(Event e) {
								List<RaptorTabItem> itemsToClose = new ArrayList<RaptorTabItem>(
										folder.getItemCount());
								for (int i = 0; i < folder.getItemCount(); i++) {
									if (i != folder.getSelectionIndex()
											&& folder.getRaptorTabItemAt(i).raptorItem
													.confirmClose()) {
										itemsToClose.add(folder
												.getRaptorTabItemAt(i));
									}
								}
								for (RaptorTabItem item : itemsToClose) {
									item.dispose();
								}

								if (folder.getItemCount() == 0) {
									restoreFolders();
								} else {
									folder.setSelection(folder
											.getSelectionIndex());
								}
							}
						});
					}

					new MenuItem(menu, SWT.SEPARATOR);

					for (int i = 0; i < Quadrant.values().length; i++) {
						if (i != folder.quad.ordinal()) {
							final Quadrant currentQuadrant = Quadrant.values()[i];
							MenuItem item = new MenuItem(menu, SWT.PUSH);
							item.setText("Move to " + currentQuadrant.name());
							item.addListener(SWT.Selection, new Listener() {
								public void handleEvent(Event e) {
									RaptorTabItem item = folder
											.getRaptorTabItemSelection();
									item.onMoveTo(folders[currentQuadrant
											.ordinal()]);

								}
							});
						}
					}
					menu.setLocation(folder.toDisplay(e.x, e.y));
					menu.setVisible(true);
					while (!menu.isDisposed() && menu.isVisible()) {
						if (!folder.getDisplay().readAndDispatch())
							folder.getDisplay().sleep();
					}
					menu.dispose();
				}
			}
		});
	}

	/**
	 * Returns true if the item is being managed by the RaptorWindow. As items
	 * are closed and disposed they are no longer managed.
	 */
	public boolean isBeingManaged(final RaptorWindowItem item) {
		boolean result = false;
		synchronized (activeItems) {
			for (RaptorTabItem currentTabItem : activeItems) {
				if (currentTabItem.raptorItem == item) {
					result = true;
					break;
				}
			}
		}
		return result;
	}

	/**
	 * Restores the window to a non maximized state. If a Quadrant contains no
	 * items, it is not visible.
	 */
	protected void restoreFolders() {
		if (LOG.isDebugEnabled()) {
			LOG.debug("Entering RaptorWindow.restoreFolders");
		}
		long startTime = System.currentTimeMillis();

		for (int i = 0; i < sashes.length; i++) {
			sashes[i].restore();
		}

		/**
		 * Set the selected item on all of the folders again. This ensures the
		 * toolbars will all be correct and activate/passivate will get invoked.
		 */
		for (int i = 0; i < folders.length; i++) {
			if (folders[i].getItemCount() > 0 && !folders[i].getMinimized()) {
				folders[i].setSelection(folders[i].getSelectionIndex());
			} else {
				folders[i].updateToolbar();
				folders[i].passivateActiveateItems();
			}
		}

		adjustToFoldersItemsMinimizied();

		if (LOG.isDebugEnabled()) {
			LOG.debug("Leaving restoreFolders execution in "
					+ (System.currentTimeMillis() - startTime) + "ms");
		}
	}

	/**
	 * Sets the ping time on the window for the specified connector. If time is
	 * -1 the label will disappear.
	 */
	public void setPingTime(final Connector connector, final long pingTime) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("setPingTime " + connector.getShortName() + " "
					+ pingTime);
		}

		getShell().getDisplay().syncExec(new Runnable() {
			public void run() {
				Label label = pingLabelsMap.get(connector.getShortName());
				if (label == null) {
					if (pingTime == -1) {
						return;
					}
					label = new Label(statusBar, SWT.NONE);
					GridData gridData = new GridData();
					gridData.grabExcessHorizontalSpace = false;
					gridData.grabExcessVerticalSpace = false;
					gridData.horizontalAlignment = SWT.END;
					gridData.verticalAlignment = SWT.CENTER;
					label.setLayoutData(gridData);
					pingLabelsMap.put(connector.getShortName(), label);
					label.setFont(Raptor.getInstance().getPreferences()
							.getFont(PreferenceKeys.APP_PING_FONT));
					label.setForeground(Raptor.getInstance().getPreferences()
							.getColor(PreferenceKeys.APP_PING_COLOR));
				}
				if (pingTime == -1) {
					label.setVisible(false);
					label.dispose();
					pingLabelsMap.remove(connector.getShortName());
					statusBar.layout(true, true);
				} else {
					label.setText(connector.getShortName() + " ping "
							+ pingTime + "ms");
					statusBar.layout(true, true);
					label.redraw();
				}
			}
		});
	}

	/**
	 * Sets the windows status message.
	 */
	public void setStatusMessage(final String newStatusMessage) {
		if (LOG.isDebugEnabled()) {
			LOG.debug("setStatusMessage " + newStatusMessage);
		}

		getShell().getDisplay().syncExec(new Runnable() {
			public void run() {
				statusLabel
						.setText(StringUtils.defaultString(newStatusMessage));
			}
		});
	}
}
