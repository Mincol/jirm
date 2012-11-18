package co.jirm.orm.builder.update;

import static com.google.common.collect.Maps.newLinkedHashMap;

import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Map.Entry;

import co.jirm.core.util.JirmPrecondition;
import co.jirm.mapper.copy.AbstractCopyBuilder;
import co.jirm.mapper.definition.SqlParameterDefinition;
import co.jirm.orm.dao.JirmOptimisticLockException;

import com.google.common.base.Optional;


public class UpdateObjectBuilder<T> extends AbstractCopyBuilder<UpdateObjectBuilder<T>> {

	private final UpdateBuilderFactory<T> updateBuilderFactory;
	private final LinkedHashMap<String, Object> object;
	
	private UpdateObjectBuilder(
			UpdateBuilderFactory<T> factory,
			LinkedHashMap<String, Object> object) {
		super();
		this.updateBuilderFactory = factory;
		this.object = object;
	}
	
	static <T> UpdateObjectBuilder<T> newInstance(
			UpdateBuilderFactory<T> factory, 
			LinkedHashMap<String, Object> object) {
		return new UpdateObjectBuilder<T>(factory, object);
	}
	
	@Override
	public UpdateObjectBuilder<T> exclude(String... properties) {
		checkProperties(properties);
		return super.exclude(properties);
	}

	@Override
	public UpdateObjectBuilder<T> include(String... properties) {
		checkProperties(properties);
		return super.include(properties);
	}

	private void checkProperties(String... properties) {
		for (String p : properties) {
			JirmPrecondition.check.argument(
					updateBuilderFactory.getDefinition().getParameters().containsKey(p), 
					"Property {} not found for object: {}", p, updateBuilderFactory.getDefinition().getObjectType());
		}
	}

	public void execute() {
		LinkedHashMap<String, Object> m = object;
		LinkedHashMap<String, Object> where = newLinkedHashMap();
		Iterator<Entry<String, Object>> it = m.entrySet().iterator();
		while(it.hasNext()) {
			Entry<String, Object> e = it.next();
			Optional<SqlParameterDefinition> p = updateBuilderFactory.getDefinition().resolveParameter(e.getKey());
			if (p.isPresent()) {
				if (p.get().isId()) {
					where.put(e.getKey(), e.getValue());
					it.remove();
				}
				else if (p.get().isVersion()) {
					Object o = e.getValue();
					JirmPrecondition.check.state(o instanceof Number, 
							"Property: {}, @Version only supports numerics", e.getKey());
					Number n = (Number) o;
					where.put(e.getKey(), n.intValue());
					e.setValue(n.intValue() + 1);
				}
				else if (excludeProperties.contains(p.get().getParameterName())) {
					it.remove();
				}
				else if( ! includeProperties.isEmpty() && ! includeProperties.contains(p.get().getParameterName()) ) {
					it.remove();
				}
			}
		}
		JirmPrecondition.check.state(!where.isEmpty(), "where should not be empty");
		int results = update(m, where);
		if (results < 1) {
			throw new JirmOptimisticLockException("Failed to update object: {}, where: {}", 
					updateBuilderFactory.getDefinition().getObjectType(),
					where);
		}
	}
	
	private int update(Map<String,Object> setValues, Map<String, Object> filters) {
		return updateBuilderFactory
				.update()
				.setAll(setValues)
				.where().propertyAll(filters)
				.execute();
	}

	@Override
	protected UpdateObjectBuilder<T> getSelf() {
		return this;
	}

}
