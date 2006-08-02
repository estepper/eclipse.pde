/*******************************************************************************
 * Copyright (c) 2003, 2006 IBM Corporation and others.
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
/*
 * Created on Jan 26, 2004
 *
 * To change the template for this generated file go to
 * Window - Preferences - Java - Code Generation - Code and Comments
 */
package org.eclipse.pde.internal.ui.editor;

import java.util.ResourceBundle;

import org.eclipse.core.resources.IMarker;
import org.eclipse.core.runtime.CoreException;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.ITextSelection;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.ILabelProvider;
import org.eclipse.jface.viewers.IPostSelectionProvider;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.ISelectionProvider;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.pde.core.IBaseModel;
import org.eclipse.pde.internal.core.text.AbstractEditingModel;
import org.eclipse.pde.internal.core.text.IDocumentNode;
import org.eclipse.pde.internal.core.text.IDocumentRange;
import org.eclipse.pde.internal.core.text.IEditingModel;
import org.eclipse.pde.internal.ui.IHelpContextIds;
import org.eclipse.pde.internal.ui.PDEPlugin;
import org.eclipse.pde.internal.ui.editor.actions.FormatAction;
import org.eclipse.pde.internal.ui.editor.actions.HyperlinkAction;
import org.eclipse.pde.internal.ui.editor.actions.PDEActionConstants;
import org.eclipse.pde.internal.ui.editor.context.InputContext;
import org.eclipse.pde.internal.ui.editor.text.PDESelectAnnotationRulerAction;
import org.eclipse.swt.SWT;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.editors.text.EditorsUI;
import org.eclipse.ui.editors.text.TextEditor;
import org.eclipse.ui.forms.IManagedForm;
import org.eclipse.ui.forms.editor.FormEditor;
import org.eclipse.ui.forms.editor.IFormPage;
import org.eclipse.ui.ide.IDE;
import org.eclipse.ui.ide.IGotoMarker;
import org.eclipse.ui.texteditor.ChainedPreferenceStore;
import org.eclipse.ui.texteditor.ContentAssistAction;
import org.eclipse.ui.texteditor.DefaultRangeIndicator;
import org.eclipse.ui.texteditor.ITextEditorActionConstants;
import org.eclipse.ui.texteditor.ITextEditorActionDefinitionIds;

public abstract class PDESourcePage extends TextEditor implements IFormPage, IGotoMarker, ISelectionChangedListener {
	
	private static String RES_BUNDLE_LOCATION = "org.eclipse.pde.internal.ui.editor.text.ConstructedPDEEditorMessages"; //$NON-NLS-1$
	private static ResourceBundle fgBundleForConstructedKeys = ResourceBundle.getBundle(RES_BUNDLE_LOCATION);
	public static ResourceBundle getBundleForConstructedKeys() {
		return fgBundleForConstructedKeys;
	}
	
	/**
	 * Updates the OutlinePage selection and this editor's range indicator.
	 * 
	 * @since 3.0
	 */
	private class PDESourcePageChangedListener implements
			ISelectionChangedListener {

		/**
		 * Installs this selection changed listener with the given selection
		 * provider. If the selection provider is a post selection provider,
		 * post selection changed events are the preferred choice, otherwise
		 * normal selection changed events are requested.
		 * 
		 * @param selectionProvider
		 */
		public void install(ISelectionProvider selectionProvider) {
			if (selectionProvider != null) {
				if (selectionProvider instanceof IPostSelectionProvider) {
					IPostSelectionProvider provider = (IPostSelectionProvider) selectionProvider;
					provider.addPostSelectionChangedListener(this);
				} else {
					selectionProvider.addSelectionChangedListener(this);					
				}
			}
 		}

		/*
		 * @see org.eclipse.jface.viewers.ISelectionChangedListener#selectionChanged(org.eclipse.jface.viewers.SelectionChangedEvent)
		 */
		public void selectionChanged(SelectionChangedEvent event) {
			ISelection selection = event.getSelection();
			if (!selection.isEmpty() && selection instanceof ITextSelection) {
				IDocumentRange rangeElement = getRangeElement(((ITextSelection) selection).getOffset(), false);
				if (rangeElement != null) {
					setHighlightRange(rangeElement, false);
				} else {
					resetHighlightRange();
				}
				// notify outline page
				if (PDEPlugin.getDefault().getPreferenceStore().getBoolean(
						"ToggleLinkWithEditorAction.isChecked")) { //$NON-NLS-1$
					fOutlinePage.removeSelectionChangedListener(fOutlineSelectionChangedListener);
					if (rangeElement != null)
						fOutlinePage.setSelection(new StructuredSelection(rangeElement));
					else
						fOutlinePage.setSelection(StructuredSelection.EMPTY);
					fOutlinePage.addSelectionChangedListener(fOutlineSelectionChangedListener);
				}
			}
		}

		/**
		 * Removes this selection changed listener from the given selection
		 * provider.
		 * 
		 * @param selectionProviderstyle
		 */
		public void uninstall(ISelectionProvider selectionProvider) {
			if (selectionProvider != null) {
				if (selectionProvider instanceof IPostSelectionProvider) {
					IPostSelectionProvider provider = (IPostSelectionProvider) selectionProvider;
					provider.removePostSelectionChangedListener(this);
				} else {
					selectionProvider.removeSelectionChangedListener(this);
				}
			}
		}

	}

	/**
	 * The editor selection changed listener.
	 * 
	 * @since 3.0
	 */
	private PDESourcePageChangedListener fEditorSelectionChangedListener;
	private PDEFormEditor fEditor;
	private Control fControl;
	private int fIndex;
	private String fId;
	private InputContext fInputContext;
	private ISortableContentOutlinePage fOutlinePage;
	private ISelectionChangedListener fOutlineSelectionChangedListener;
	protected Object fSelection;

	public PDESourcePage(PDEFormEditor editor, String id, String title) {
		fId = id;
		initialize(editor);
		IPreferenceStore[] stores = new IPreferenceStore[2];
		stores[0] = PDEPlugin.getDefault().getPreferenceStore();
		stores[1] = EditorsUI.getPreferenceStore();
		setPreferenceStore(new ChainedPreferenceStore(stores));
		setRangeIndicator(new DefaultRangeIndicator());
		if (isSelectionListener())
			getEditor().getSite().getSelectionProvider().addSelectionChangedListener(this);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.editor.IFormPage#initialize(org.eclipse.ui.forms.editor.FormEditor)
	 */
	public void initialize(FormEditor editor) {
		fEditor = (PDEFormEditor)editor;
	}
	
	public void dispose() {
		if (fEditorSelectionChangedListener != null)  {
			fEditorSelectionChangedListener.uninstall(getSelectionProvider());
			fEditorSelectionChangedListener= null;
		}
		if (fOutlinePage != null) {
			fOutlinePage.dispose();
			fOutlinePage = null;
		}
		if (isSelectionListener())
			getEditor().getSite().getSelectionProvider().removeSelectionChangedListener(this);
		super.dispose();
	}

	protected abstract ILabelProvider createOutlineLabelProvider();
	
	protected abstract ITreeContentProvider createOutlineContentProvider();
	
	protected abstract ViewerSorter createOutlineSorter();
	
	protected abstract void outlineSelectionChanged(SelectionChangedEvent e);
	
	protected ViewerSorter createDefaultOutlineSorter() {
		return null;
	}
	
	protected ISortableContentOutlinePage createOutlinePage() {
		SourceOutlinePage sourceOutlinePage=
		new SourceOutlinePage(
				(IEditingModel) getInputContext().getModel(),
				createOutlineLabelProvider(), createOutlineContentProvider(),
				createDefaultOutlineSorter(), createOutlineSorter());
		fOutlinePage = sourceOutlinePage;
		fOutlineSelectionChangedListener = new ISelectionChangedListener() {
			public void selectionChanged(SelectionChangedEvent event) {
				outlineSelectionChanged(event);
			}
		};
		fOutlinePage.addSelectionChangedListener(fOutlineSelectionChangedListener);
		getSelectionProvider().addSelectionChangedListener(sourceOutlinePage);
		fEditorSelectionChangedListener= new PDESourcePageChangedListener();
		fEditorSelectionChangedListener.install(getSelectionProvider());
		return fOutlinePage;
	}

	public ISortableContentOutlinePage getContentOutline() {
		if (fOutlinePage == null)
			fOutlinePage = createOutlinePage();
		return fOutlinePage;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.editor.IFormPage#getEditor()
	 */
	public FormEditor getEditor() {
		return fEditor;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.editor.IFormPage#getManagedForm()
	 */
	public IManagedForm getManagedForm() {
		// not a form page
		return null;
	}
	
	protected void firePropertyChange(int type) {
		if (type == PROP_DIRTY) {
			fEditor.fireSaveNeeded(getEditorInput(), true);
		} else
			super.firePropertyChange(type);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.editor.IFormPage#setActive(boolean)
	 */
	public void setActive(boolean active) {
		fInputContext.setSourceEditingMode(active);
	}

	public boolean canLeaveThePage() {
		return true;
	}

	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.editor.IFormPage#isActive()
	 */
	public boolean isActive() {
		return this.equals(fEditor.getActivePageInstance());
	}
	
	public void createPartControl(Composite parent) {
		super.createPartControl(parent);
		Control[] children = parent.getChildren();
		fControl = children[children.length - 1];
		
		PlatformUI.getWorkbench().getHelpSystem().setHelp(fControl, IHelpContextIds.MANIFEST_SOURCE_PAGE);
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.editor.IFormPage#getPartControl()
	 */
	public Control getPartControl() {
		return fControl;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.editor.IFormPage#getId()
	 */
	public String getId() {
		return fId;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.editor.IFormPage#getIndex()
	 */
	public int getIndex() {
		return fIndex;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.editor.IFormPage#setIndex(int)
	 */
	public void setIndex(int index) {
		fIndex = index;
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.editor.IFormPage#isSource()
	 */
	public boolean isEditor() {
		return true;
	}
	
	/**
	 * @return Returns the inputContext.
	 */
	public InputContext getInputContext() {
		return fInputContext;
	}
	
	/**
	 * @param inputContext The inputContext to set.
	 */
	public void setInputContext(InputContext inputContext) {
		fInputContext = inputContext;
		setDocumentProvider(inputContext.getDocumentProvider());
	}
	
	/* (non-Javadoc)
	 * @see org.eclipse.ui.forms.editor.IFormPage#focusOn(java.lang.Object)
	 */
	public boolean selectReveal(Object object) {
		if (object instanceof IMarker) {
			IDE.gotoMarker(this, (IMarker)object);
			return true;
		}
		return false;
	}
	
	public IDocumentRange getRangeElement(int offset, boolean searchChildren) {
		return null;
	}

	public void setHighlightRange(IDocumentRange range, boolean moveCursor) {
		int offset = range.getOffset();
		if (offset == -1) {
			resetHighlightRange();
			return;
		}
		
		ISourceViewer sourceViewer = getSourceViewer();
		if (sourceViewer == null)
			return;

		IDocument document = sourceViewer.getDocument();
		if (document == null)
			return;

		int length = range.getLength();
		setHighlightRange(offset, length == -1 ? 1 : length, moveCursor);
	}
	
	public void setSelectedRange(IDocumentRange range, boolean fullNodeSelection) {
		ISourceViewer sourceViewer = getSourceViewer();
		if (sourceViewer == null)
			return;

		IDocument document = sourceViewer.getDocument();
		if (document == null)
			return;

		int offset;
		int length;
		if (range instanceof IDocumentNode && !fullNodeSelection) {
			length = ((IDocumentNode)range).getXMLTagName().length();
			offset = range.getOffset() + 1;
		} else {
			length = range.getLength();
			offset = range.getOffset();
		}
		sourceViewer.setSelectedRange(offset, length);
	}
	
	public int getOrientation() {
		return SWT.LEFT_TO_RIGHT;
	}
	
	protected void createActions() {
		super.createActions();
		PDESelectAnnotationRulerAction action = new PDESelectAnnotationRulerAction(
				getBundleForConstructedKeys(), "PDESelectAnnotationRulerAction.", this, getVerticalRuler()); //$NON-NLS-1$
		setAction(ITextEditorActionConstants.RULER_CLICK, action);
		PDEFormEditorContributor contributor = fEditor == null ? null : fEditor.getContributor();
		if (contributor != null) {
			if (contributor instanceof PDEFormTextEditorContributor) {
				PDEFormTextEditorContributor textContributor = (PDEFormTextEditorContributor)contributor;
				setAction(PDEActionConstants.OPEN, textContributor.getHyperlinkAction());
				setAction(PDEActionConstants.FORMAT, textContributor.getFormatAction());			
				if (textContributor.supportsContentAssist())
					createContentAssistAction();
			}
		}
	}
	
	private void createContentAssistAction() {
		IAction contentAssist = new ContentAssistAction(
				getBundleForConstructedKeys(), "ContentAssistProposal.", this); //$NON-NLS-1$
		contentAssist.setActionDefinitionId(ITextEditorActionDefinitionIds.CONTENT_ASSIST_PROPOSALS);
		setAction("ContentAssist", contentAssist); //$NON-NLS-1$
		markAsStateDependentAction("ContentAssist", true); //$NON-NLS-1$		
	}
	
	public final void selectionChanged(SelectionChangedEvent event) {
		if (event.getSource() == getSelectionProvider())
			return;
		ISelection sel = event.getSelection();
		if (sel instanceof ITextSelection)
			return;
		if (sel instanceof IStructuredSelection)
			fSelection = ((IStructuredSelection)sel).getFirstElement();
		else
			fSelection = null;
	}
	
	/*
	 * Locate an IDocumentRange, subclasses that want to 
	 * highlight text components based on site selection
	 * should override this method.
	 */
	protected IDocumentRange findRange() {
		return null;
	}
	
	public void updateTextSelection() {
		IDocumentRange range = findRange();
		if (range == null)
			return;
		IBaseModel model = getInputContext().getModel();
		if (!(model instanceof AbstractEditingModel))
			return;
		
		if (range.getOffset() == -1 || isDirty()) {
			try {
				((AbstractEditingModel)model).adjustOffsets(((AbstractEditingModel)model).getDocument());
			} catch (CoreException e) {
			}
			range = findRange();
		}
		setHighlightRange(range, true);
		setSelectedRange(range, true);
	}
	
	/*
	 * Subclasses that wish to provide PDEFormPage -> PDESourcePage
	 * selection persistence should override this and return true.
	 */
	protected boolean isSelectionListener() {
		return false;
	}
	
	public ISourceViewer getViewer() {
		return getSourceViewer();
	}

	protected void editorContextMenuAboutToShow(IMenuManager menu) {
		PDEFormEditorContributor contributor = fEditor == null ? null : fEditor.getContributor();
		if (contributor instanceof PDEFormTextEditorContributor) {
			PDEFormTextEditorContributor textContributor = (PDEFormTextEditorContributor)contributor;
			HyperlinkAction action = textContributor.getHyperlinkAction();
			if (action != null && action.isEnabled())
				menu.add(action);
			FormatAction formatManifestAction = textContributor.getFormatAction();
			if (formatManifestAction != null && formatManifestAction.isEnabled())
				menu.add(formatManifestAction);
		}
		super.editorContextMenuAboutToShow(menu);
	}
}
