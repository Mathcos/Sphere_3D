import java.awt.BorderLayout;
import java.awt.EventQueue;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextField;

public class AppLauncher {

	/*public static void main(String[] args) {
	       new MainFrame().setVisible(true);
	    }*/
	
	
	private JFrame frame;
	private JTextField textField;

	/**
	 * Launch the application.
	 */
	public static void main(String[] args) {
		EventQueue.invokeLater(new Runnable() {
			public void run() {
				try {
					AppLauncher window = new AppLauncher();
					window.frame.setVisible(true);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
		});
	}

	/**
	 * Create the application.
	 */
	public AppLauncher() {
		initialize();
	}

	/**
	 * Initialize the contents of the frame.
	 */
	private void initialize() {
		//frame = new JFrame();
		frame = new MainFrame();
		frame.setBounds(100, 100, 450, 300);
		frame.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		//frame.getContentPane().add();
		JButton btnNewButton = new JButton("New button");
		//frame.getContentPane().add(btnNewButton, BorderLayout.WEST);
		
		textField = new JTextField();
		//frame.getContentPane().add(textField, BorderLayout.CENTER);
		textField.setColumns(10);
	}

}
