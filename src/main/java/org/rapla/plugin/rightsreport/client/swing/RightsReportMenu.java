package org.rapla.plugin.rightsreport.client.swing;

import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.swing.Icon;
import javax.swing.MenuElement;

import org.rapla.client.extensionpoints.AdminMenuExtension;
import org.rapla.framework.RaplaContext;
import org.rapla.framework.RaplaException;
import org.rapla.client.swing.RaplaGUIComponent;
import org.rapla.client.swing.toolkit.DialogUI;
import org.rapla.client.swing.toolkit.RaplaMenuItem;
import org.rapla.inject.Extension;

@Extension(id=RightsReportMenu.PLUGIN_ID, provides=AdminMenuExtension.class)
public class RightsReportMenu extends RaplaGUIComponent implements AdminMenuExtension, ActionListener{

    public static final String PLUGIN_ID = "report";
	private RaplaMenuItem report;
	final String name = getString("user") +"/"+ getString("groups") + " "+getString("report") ;
    private final Provider<RaplaRightsReport> rightsReportProvider;

	@Inject
	public RightsReportMenu(RaplaContext context, Provider<RaplaRightsReport> rightsReportProvider) {
		super(context);
        this.rightsReportProvider = rightsReportProvider;
		
		report = new RaplaMenuItem("report");
		report.getMenuElement().setText( name);
		final Icon icon = getIcon("icon.info_small");
		report.getMenuElement().setIcon( icon);
		report.addActionListener( this);
	}

	public String getId() {
        return PLUGIN_ID;
	}

	@Override
	public MenuElement getMenuElement() {
		return report;
	}
	
	public void actionPerformed( ActionEvent e )
    {
        try {
           
        	RaplaRightsReport report = rightsReportProvider.get();
            DialogUI dialog = DialogUI.create( getContext(),getMainComponent(),true, report.getComponent(), new String[] {getString("ok")});
            dialog.setTitle( name);
            dialog.setSize( 650, 550);
            report.show();
            dialog.startNoPack();
        } catch (RaplaException ex) {
            showException( ex, getMainComponent());
        }
    }

}