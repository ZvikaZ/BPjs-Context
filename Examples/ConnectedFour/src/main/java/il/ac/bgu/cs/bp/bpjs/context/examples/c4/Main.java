package il.ac.bgu.cs.bp.bpjs.context.examples.c4;

import il.ac.bgu.cs.bp.bpjs.context.ContextService;
import il.ac.bgu.cs.bp.bpjs.model.BEvent;
import il.ac.bgu.cs.bp.bpjs.model.BProgram;

public class Main {
	void run(String dbPopulationScript, String persistenceUnit) throws InterruptedException {
		System.out.println(">>>>>>>>>>>>>>>>>> Connected Four example <<<<<<<<<<<<<<<<<<<");

		ContextService contextService = ContextService.getInstance();
		contextService.initFromResources(persistenceUnit, dbPopulationScript, "program.js", "assertions.js");
		contextService.run();
		BProgram bprog = contextService.getBProgram();

		// Simulation of external events
		Thread.sleep(3000);
		ContextService.getContextInstances("Cell")
				.forEach(cell -> bprog.enqueueExternalEvent(new BEvent("Click",cell)));
				
		Thread.sleep(6000);
		contextService.close();
	}


	public static void main(String[] args) throws InterruptedException {
		new Main().run("db_population.js", "ContextDB");
	}
}