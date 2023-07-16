package com.google.scholar.frame;

import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.event.ActionEvent;
import java.awt.event.ActionListener;

import javax.swing.JButton;
import javax.swing.JFrame;
import javax.swing.JTextField;
import javax.swing.SwingWorker;

import com.google.scholar.app.GoogleScholarApplication;

public class CrawlerFrame extends JFrame {
	
    private static final long serialVersionUID = 1L;
    private static JButton run;
    private static JButton exit;
    private static JTextField textField;
    private static GoogleScholarApplication googleScholarCrawlerVer2;

    public CrawlerFrame() {
    	googleScholarCrawlerVer2 = new GoogleScholarApplication();
        this.setTitle("Google Scholar Crawler");
        this.setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        this.setSize(500, 400);        
        
        GridBagLayout layout = new GridBagLayout();
        GridBagConstraints constraints = new GridBagConstraints();
        this.setLayout(layout);

        run = new JButton("시작");
        exit = new JButton("종료");
        textField = new JTextField();
        

        constraints.gridx = 0;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 0.3;
        layout.setConstraints(run, constraints);
        this.add(run);

        constraints.gridx = 2;
        constraints.gridy = 0;
        constraints.gridwidth = 2;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 0.3;
        layout.setConstraints(exit, constraints);
        this.add(exit);

        constraints.gridx = 0;
        constraints.gridy = 1;
        constraints.gridwidth = 4;
        constraints.fill = GridBagConstraints.BOTH;
        constraints.weightx = 1.0;
        constraints.weighty = 1.0;
        layout.setConstraints(textField, constraints);
        textField.setEditable(false);
        this.add(textField);
        
        this.setVisible(true);

        run.addActionListener(new RunListener(this));
        exit.addActionListener(new ExitListener());
    }

    class RunListener implements ActionListener {
    	JFrame frame;
    	private RunListener(JFrame f) {
    		frame = f;
    	}
    	
        @Override
        public void actionPerformed(ActionEvent e) {
            run.setEnabled(false);
            run.setText("실행 중입니다.");
            
            SwingWorker<Void, Void> worker = new SwingWorker<Void, Void>() {
            	
                @Override
                protected Void doInBackground() throws Exception {    
                	googleScholarCrawlerVer2.setCrawlerFrame(frame);
                    googleScholarCrawlerVer2.main(new String[0]);
                    return null;
                }

            };
            
            worker.execute();
        }
    }

    class ExitListener implements ActionListener {
        @Override
        public void actionPerformed(ActionEvent e) {
        	try {
        		googleScholarCrawlerVer2.chromeDriverKill();
			} catch (Exception e1) {
				e1.printStackTrace();
			}
            System.exit(0);
        }
    }

	public static void setLog(String newMsg) {
		System.out.println(newMsg);
		textField.setText(newMsg);
	}
}