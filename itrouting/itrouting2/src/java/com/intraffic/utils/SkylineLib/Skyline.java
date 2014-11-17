package com.intraffic.utils.SkylineLib;
import java.util.HashMap;
import java.util.LinkedList;

public class Skyline {

	private boolean dominate(Double[] a, Double[] b) {
		boolean less_and_equals = true;
		boolean less_than = false;

		for(int i = 0; i < a.length; i++) {
			if(a[i] < b[i])
				less_than = true;

			if(a[i] > b[i])
				less_and_equals = false;
		}

		return less_and_equals && less_than;

	}
	public LinkedList<String> getSkyline(HashMap<String,Double[]> candidates_skyline) {
		LinkedList<String> skyline = new LinkedList<String>();
		for(String key : candidates_skyline.keySet()) {
			boolean isSkyline = true;
			for(String key2 : candidates_skyline.keySet()) {
				if(this.dominate(
					candidates_skyline.get(key2),
					candidates_skyline.get(key)))
					isSkyline = false;
			}
			if(isSkyline) {
				skyline.add(key);
				System.out.println(key);
			}
		}
		return skyline;
	}
}