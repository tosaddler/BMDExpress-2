/**
 * PolyFitThread.java
 *
 *
 */

package com.sciome.bmdexpress2.util.bmds.thread;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.CountDownLatch;

import com.sciome.bmdexpress2.mvp.model.probe.ProbeResponse;
import com.sciome.bmdexpress2.mvp.model.stat.PolyResult;
import com.sciome.bmdexpress2.mvp.model.stat.StatResult;
import com.sciome.bmdexpress2.util.bmds.FilePolyFit;
import com.sciome.bmdexpress2.util.bmds.ModelInputParameters;

public class PolyFitThread extends Thread implements IFitThread
{
	private CountDownLatch			cdLatch;
	private FilePolyFit				fPolyFit			= null;

	private int						degree;
	private ModelInputParameters	inputParameters;

	private float[]					doses;

	private final int[]				adversDirections	= { 0, 1, -1 };

	List<ProbeResponse>				probeResponses;
	List<StatResult>				polyResults;
	int								numThreads;
	int								instanceIndex;
	private IModelProgressUpdater	progressUpdater;
	private IProbeIndexGetter		probeIndexGetter;

	private boolean					cancel				= false;

	public PolyFitThread(CountDownLatch cDownLatch, int degree, List<ProbeResponse> probeResponses,
			List<StatResult> polyResults, int numThreads, int instanceIndex, int killTime,
			IModelProgressUpdater progressUpdater, IProbeIndexGetter probeIndexGetter)
	{
		this.progressUpdater = progressUpdater;
		this.cdLatch = cDownLatch;
		this.degree = degree;
		this.probeResponses = probeResponses;
		this.instanceIndex = instanceIndex;
		this.numThreads = numThreads;
		this.polyResults = polyResults;
		this.probeIndexGetter = probeIndexGetter;

		fPolyFit = new FilePolyFit(killTime);

	}

	public void setDoses(float[] doses)
	{
		this.doses = doses;
	}

	public void setObjects(int degree, ModelInputParameters inputParameters)
	{
		this.degree = degree;
		this.inputParameters = inputParameters;

	}

	@Override
	public void run()
	{
		doFiledPolyFit();

		try
		{
			cdLatch.countDown();
		}
		catch (Exception e)
		{
			e.printStackTrace();
		}
	}

	private void doFiledPolyFit()
	{

		Random rand = new Random(System.nanoTime());
		int randInt = Math.abs(rand.nextInt());

		Integer probeIndex = probeIndexGetter.getNextProbeIndex();
		while (probeIndex != null)
		{

			PolyResult polyResult = (PolyResult) polyResults.get(probeIndex);
			try
			{
				double direction = 0;
				String id = probeResponses.get(probeIndex).getProbe().getId().replaceAll("\\s", "_");
				float[] responses = probeResponses.get(probeIndex).getResponseArray();
				inputParameters.setAdversDirection(adversDirections[0]);

				if (cancel)
					break;

				double[] results = fPolyFit.fitModel(String.valueOf(randInt) + "_" + id, inputParameters,
						doses, responses);

				if (results[6] > 0)
					direction = 1;
				else
					direction = -1;

				if (results != null)
					fillOutput(results, polyResult);
			}
			catch (Exception e)
			{
				e.printStackTrace();
			}
			this.progressUpdater.incrementModelsComputed();
			probeIndex = probeIndexGetter.getNextProbeIndex();
		}
	}

	private void fillOutput(double[] results, PolyResult polyResult)
	{
		polyResult.setBMD(results[0]);
		polyResult.setBMDL(results[1]);
		polyResult.setBMDU(results[2]);
		polyResult.setFitPValue(results[3]);
		polyResult.setFitLogLikelihood(results[4]);
		polyResult.setAIC(results[5]);

		int direction = 1;

		if (results[7] < 0)
		{
			direction = -1;
		}
		polyResult.setCurveParameters(Arrays.copyOfRange(results, 6, results.length));
		polyResult.setAdverseDirection((short) direction);
		polyResult.setSuccess("" + fPolyFit.isSuccess());
	}

	@Override
	public void cancel()
	{
		cancel = true;
	}
}
