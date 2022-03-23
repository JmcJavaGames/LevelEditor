package com.javagames.leveleditor.dialogs;

import com.javagames.leveleditor.model.ImageSize;

import javax.swing.*;
import java.awt.*;
import java.awt.event.ComponentAdapter;
import java.awt.event.ComponentEvent;

public class RequestSizeDialog extends JDialog {
    private ImageSize size;

    public RequestSizeDialog(Frame owner, String title) {
        super(owner, title, ModalityType.APPLICATION_MODAL);
        setLayout(new GridLayout(3, 2, 3, 3));

        JLabel xLabel = new JLabel("Width:", SwingConstants.RIGHT);
        JLabel yLabel = new JLabel("Height:", SwingConstants.RIGHT);

        JTextField xField = new JTextField(10);
        JTextField yField = new JTextField(10);

        JButton okButton = new JButton("OK");
        okButton.addActionListener(e -> {
            try {
                int width = Integer.parseInt(xField.getText());
                int height = Integer.parseInt(yField.getText());
                size = ImageSize.of(width, height);
            } catch (NumberFormatException ignored) {
            }
            dispose();
        });

        JButton cancelButton = new JButton("Cancel");
        cancelButton.addActionListener(e -> dispose());

        add(xLabel);
        add(xField);
        add(yLabel);
        add(yField);
        add(okButton);
        add(cancelButton);

        setDefaultCloseOperation(DISPOSE_ON_CLOSE);

        addComponentListener(new ComponentAdapter() {
            public void componentShown(ComponentEvent ce) {
                xField.requestFocusInWindow();
            }
        });
    }

    public ImageSize getEnteredSizes() {
        return size;
    }
}
