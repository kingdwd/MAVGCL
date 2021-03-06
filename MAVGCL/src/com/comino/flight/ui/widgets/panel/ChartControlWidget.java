/****************************************************************************
 *
 *   Copyright (c) 2017,2018 Eike Mansfeld ecm@gmx.de. All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met:
 *
 * 1. Redistributions of source code must retain the above copyright
 *    notice, this list of conditions and the following disclaimer.
 * 2. Redistributions in binary form must reproduce the above copyright
 *    notice, this list of conditions and the following disclaimer in
 *    the documentation and/or other materials provided with the
 *    distribution.
 * 3. Neither the name of the copyright holder nor the names of its
 *    contributors may be used to endorse or promote products derived
 *    from this software without specific prior written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT
 * LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS
 * FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL THE
 * COPYRIGHT OWNER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT,
 * INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING,
 * BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES; LOSS
 * OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER CAUSED
 * AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT
 * LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN
 * ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED OF THE
 * POSSIBILITY OF SUCH DAMAGE.
 *
 ****************************************************************************/

package com.comino.flight.ui.widgets.panel;


import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import com.comino.flight.FXMLLoadHelper;
import com.comino.flight.file.FileHandler;
import com.comino.flight.file.KeyFigurePreset;
import com.comino.flight.model.service.AnalysisModelService;
import com.comino.jfx.extensions.WidgetPane;
import com.comino.mav.control.IMAVController;

import javafx.application.Platform;
import javafx.event.ActionEvent;
import javafx.event.EventHandler;
import javafx.fxml.FXML;
import javafx.scene.Cursor;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Slider;
import javafx.scene.input.MouseEvent;

public class ChartControlWidget extends WidgetPane  {

	private static final Integer[] TOTAL_TIME = { 30, 60, 120, 240, 480, 1200 };


	@FXML
	private ChoiceBox<Integer> totaltime;

	@FXML
	private ComboBox<String> keyfigures;

	@FXML
	private Slider scroll;

	@FXML
	private Button save;

	private Map<Integer,IChartControl> charts = null;

	protected int totalTime_sec = 30;
	private AnalysisModelService modelService;

	private long scroll_tms = 0;

	private Map<Integer,KeyFigurePreset> presets = new HashMap<Integer,KeyFigurePreset>();

	public ChartControlWidget() {
		super(300,true);
		FXMLLoadHelper.load(this, "ChartControlWidget.fxml");
		charts = new HashMap<Integer,IChartControl>();
	}

	@FXML
	private void initialize() {

		this.modelService =  AnalysisModelService.getInstance();
		totaltime.getItems().addAll(TOTAL_TIME);
		totaltime.getSelectionModel().select(0);

		buildKeyfigureModelSelection();

		totaltime.getSelectionModel().selectedItemProperty().addListener((observable, oldValue, newValue) -> {
			totalTime_sec  = newValue.intValue();
			modelService.setTotalTimeSec(totalTime_sec);

			for(Entry<Integer, IChartControl> chart : charts.entrySet()) {
				if(chart.getValue().getTimeFrameProperty()!=null)
					chart.getValue().getTimeFrameProperty().set(newValue.intValue());
			}

			if(modelService.getModelList().size() < totalTime_sec * 1000 /  modelService.getCollectorInterval_ms()
					|| modelService.isCollecting() || modelService.getModelList().size()==0)
				scroll.setDisable(true);
			else
				scroll.setDisable(false);
			scroll.setValue(1);
		});

		scroll.setSnapToTicks(false); scroll.setSnapToPixel(false);
		scroll.setDisable(true);

//		StateProperties.getInstance().getRecordingProperty().addListener((e,o,n) -> {
//			keyfigures.setDisable(n.booleanValue()); save.setDisable(n.booleanValue());
//		});


		scroll.valueProperty().addListener((observable, oldvalue, newvalue) -> {
			if((System.currentTimeMillis() - scroll_tms)>50) {
				charts.entrySet().forEach((chart) -> {
					if(chart.getValue().getScrollProperty()!=null && chart.getValue().isVisible())
						chart.getValue().getScrollProperty().set(1f-newvalue.floatValue()/1000000);
				});
				scroll_tms = System.currentTimeMillis();
			}
		});

		scroll.valueChangingProperty().addListener((observable, oldvalue, newvalue) -> {
			charts.entrySet().forEach((chart) -> {
				if(chart.getValue().getIsScrollingProperty()!=null)
					chart.getValue().getIsScrollingProperty().set(newvalue.booleanValue());
			});
		});


		state.getLogLoadedProperty().addListener((observable, oldValue, newValue) -> {
			if(newValue.booleanValue()) {
				if(modelService.getModelList().size() < totalTime_sec * 1000 /  modelService.getCollectorInterval_ms() || modelService.isCollecting())
					scroll.setDisable(true);
				else
					scroll.setDisable(false);
				scroll.setValue(1);
			}
		});

		state.getRecordingProperty().addListener((observable, oldvalue, newvalue) -> {
			if(newvalue.intValue()!=AnalysisModelService.STOPPED) {
				scroll.setDisable(true);
				scroll.setValue(0);
				return;
			}

			if(modelService.getModelList().size() < totalTime_sec * 1000 / modelService.getCollectorInterval_ms())
				scroll.setDisable(true);
			else
				scroll.setDisable(false);
		});

		//	scroll.disableProperty().bind(StateProperties.getInstance().getRecordingProperty());
		scroll.setOnMouseClicked(new EventHandler<MouseEvent>() {

			@Override
			public void handle(MouseEvent click) {
				if (click.getClickCount() == 2) {
					scroll.setValue(scroll.getValue() == 1000000 ? 0 : 1000000);
					for(Entry<Integer, IChartControl> chart : charts.entrySet()) {
						if(chart.getValue().getScrollProperty()!=null)
							chart.getValue().getScrollProperty().set((float)(1f-scroll.getValue()/1000000));
					}
				}
			}
		});

		save.setOnAction((ActionEvent event)-> {
			saveKeyFigureSelection();
			event.consume();
		});

	}


	public void setup(IMAVController control, StatusWidget statuswidget) {
		this.control = control;
		this.modelService =  AnalysisModelService.getInstance();
		this.modelService.setTotalTimeSec(totalTime_sec);
		this.modelService.clearModelList();

		for(Entry<Integer, IChartControl> chart : charts.entrySet()) {
			if(chart.getValue().getTimeFrameProperty()!=null)
				chart.getValue().getTimeFrameProperty().set(totalTime_sec);
			if(chart.getValue().getScrollProperty()!=null)
				chart.getValue().getScrollProperty().set(1);
		}
	}


	public void addChart(int id,IChartControl chart) {
		charts.put(id,chart);
	}

	public void refreshCharts() {

		for(Entry<Integer, IChartControl> chart : charts.entrySet()) {
			if(chart.getValue().getScrollProperty()!=null)
				chart.getValue().getScrollProperty().set(1);
			chart.getValue().refreshChart();
		}
		scroll.setValue(0);
		if(modelService.getModelList().size() > totalTime_sec * 1000 /  modelService.getCollectorInterval_ms())
			scroll.setDisable(false);
	}

	private void saveKeyFigureSelection() {
		for(Entry<Integer, IChartControl> chart : charts.entrySet()) {
			presets.put(chart.getKey(), chart.getValue().getKeyFigureSelection());
		}
		FileHandler.getInstance().presetsExport(presets);
	}


	private void buildKeyfigureModelSelection() {

		keyfigures.getItems().add("Select presets...");
		keyfigures.getItems().add("Open...");

		for(String p : FileHandler.getInstance().getPresetList())
			keyfigures.getItems().add(p);

		keyfigures.setEditable(true);
		keyfigures.getEditor().setEditable(false);
		keyfigures.getEditor().setCursor(Cursor.DEFAULT);
		keyfigures.getSelectionModel().select(0);

		keyfigures.getSelectionModel().selectedIndexProperty().addListener((o,ov,nv) -> {
			Map<Integer,KeyFigurePreset> pr = null;

			switch(nv.intValue()) {
			case 0:
				break;
				//			case 1:
				//				for(Entry<Integer, KeyFigurePreset> preset : presets.entrySet()) {
				//					charts.get(preset.getKey()).setKeyFigureSeletcion(preset.getValue());
				//				}
				//				break;
			case 1:
				pr = FileHandler.getInstance().presetsImport(null);
				if(pr != null) {
					this.presets.clear(); this.presets.putAll(pr);
					for(Entry<Integer, KeyFigurePreset> preset : presets.entrySet()) {
						charts.get(preset.getKey()).setKeyFigureSeletcion(preset.getValue());
					}
				}
				break;
			default:
				pr = FileHandler.getInstance().presetsImport(keyfigures.getItems().get(nv.intValue()));
				if(pr != null) {
					this.presets.clear(); this.presets.putAll(pr);
					for(Entry<Integer, KeyFigurePreset> preset : presets.entrySet()) {
						charts.get(preset.getKey()).setKeyFigureSeletcion(preset.getValue());
					}
				}
				break;

			}
			Platform.runLater(() -> {
				keyfigures.getSelectionModel().select(0);
			});
		});

	}

}
