/*******************************************************************************
 *  Copyright (c) 2003, 2015 IBM Corporation and others.
 *  All rights reserved. This program and the accompanying materials
 *  are made available under the terms of the Eclipse Public License v1.0
 *  which accompanies this distribution, and is available at
 *  http://www.eclipse.org/legal/epl-v10.html
 * 
 *  Contributors:
 *     IBM Corporation - initial API and implementation
 *******************************************************************************/
package org.eclipse.pde.internal.ui.editor;

import org.eclipse.jface.text.IDocument;
import org.eclipse.jface.text.source.ISourceViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerComparator;
import org.eclipse.pde.internal.core.text.IDocumentKey;
import org.eclipse.pde.internal.core.util.PropertiesUtil;

public abstract class KeyValueSourcePage extends PDEProjectionSourcePage {

	public KeyValueSourcePage(PDEFormEditor editor, String id, String title) {
		super(editor, id, title);
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.neweditor.PDESourcePage#createViewerSorter()
	 */
	@Override
	public ViewerComparator createDefaultOutlineComparator() {
		return new ViewerComparator() {
			@Override
			public int compare(Viewer viewer, Object e1, Object e2) {
				if ((e1 instanceof IDocumentKey) && (e2 instanceof IDocumentKey)) {
					IDocumentKey key1 = (IDocumentKey) e1;
					IDocumentKey key2 = (IDocumentKey) e2;
					return key1.getOffset() < key2.getOffset() ? -1 : 1;
				}
				// Bundle manifest header elements
				// Do not sort by default
				return 0;
			}
		};
	}

	public void setHighlightRange(IDocumentKey key) {
		ISourceViewer sourceViewer = getSourceViewer();
		if (sourceViewer == null)
			return;

		IDocument document = sourceViewer.getDocument();
		if (document == null)
			return;

		int offset = key.getOffset();
		int length = key.getLength();

		if (offset == -1 || length == -1)
			return;
		setHighlightRange(offset, length, true);
		int nameLength = PropertiesUtil.createWritableName(key.getName()).length();
		sourceViewer.setSelectedRange(offset, Math.min(nameLength, length));
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDESourcePage#createOutlineSorter()
	 */
	@Override
	public ViewerComparator createOutlineComparator() {
		return new ViewerComparator();
	}

	/* (non-Javadoc)
	 * @see org.eclipse.pde.internal.ui.editor.PDEProjectionSourcePage#isQuickOutlineEnabled()
	 */
	@Override
	public boolean isQuickOutlineEnabled() {
		return true;
	}

}
