package com.google.scholar.frame;

import java.awt.GridLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

public class LoginFrame extends JFrame{

	private static final long serialVersionUID = 1L; 
	private static JTextField ID, PW;
	private JFrame crawlerFrame;
	
	public LoginFrame() {
		this.setTitle("Google Scholar Crawler");
		this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
		this.setSize(300,200);
		this.setVisible(true);		
		this.setLayout(new GridLayout(0,1));
		add(new JLabel("  LOGIN"));
		
		JPanel panel1 = new JPanel();
		JPanel panel2 = new JPanel();
	
		panel1.add(new JLabel("ID : "));
		panel2.add(new JLabel("PW : "));
		
		ID = new JTextField(20);
		PW = new JPasswordField(20);
		panel1.add(ID);	
		panel2.add(PW);	
		
		this.add(panel1);
		this.add(panel2);
		
		JButton button = new JButton("로그인");
		this.add(button);
		this.setVisible(true);
		
		button.addActionListener(new LoginListener(this));
	}
	
	class LoginListener implements ActionListener{
		JFrame frame;
		public LoginListener(JFrame f){
			frame =f;
		}
		@Override
		public void actionPerformed(ActionEvent arg0) {
			String n =ID.getText();			
			String a =PW.getText();
			
			if(n.equals("test") && a.equals("1234")) {
				JOptionPane.showMessageDialog(frame, "로그인 성공");						        
				crawlerFrame = new CrawlerFrame();
		        frame.setVisible(false);
		        
			}
			else JOptionPane.showMessageDialog(frame, "실패");
		}
	}
		
	public static void main(String[] args) {
		new LoginFrame();
	}

}
