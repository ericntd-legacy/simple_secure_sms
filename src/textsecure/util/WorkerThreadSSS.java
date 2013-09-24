/**
 * Copyright (C) 2011 Whisper Systems
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */
package textsecure.util;

import java.util.List;

import android.util.Log;

public class WorkerThreadSSS extends Thread {
	// debugging
	private final String TAG = "WorkerThreadSSS";

  private final List<Runnable> workQueue;

  public WorkerThreadSSS(List<Runnable> workQueue, String name) {
    super(name);
    this.workQueue = workQueue;
  }

  private Runnable getWork() {
    synchronized (workQueue) {
      try {
        while (workQueue.isEmpty()) workQueue.wait();

        return workQueue.remove(0);
      } catch (InterruptedException ie) {
        throw new AssertionError(ie);
      }
    }
  }

  @Override
  public void run() {
    //for (;;) //same as while (true) {}
	  while (true)
    {
    	Runnable tmp = getWork();
    	if (tmp==null) {
		//if (workQueue.size()==0) {
			// no 
    		break; // I was afraid that this will never stop, just cracking it basically
    	}
    	else {
    		Log.w(TAG, "worker thread is executing a job "+tmp.toString());
    		tmp.run();
    	}
    	
    }
  }
}
