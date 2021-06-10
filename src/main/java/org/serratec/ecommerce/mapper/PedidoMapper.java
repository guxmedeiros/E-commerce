package org.serratec.ecommerce.mapper;

import org.serratec.ecommerce.dto.PedidoDTO;
import org.serratec.ecommerce.dto.PedidoDTOAll;
import org.serratec.ecommerce.dto.PedidoDTOComp;
import org.serratec.ecommerce.entities.PedidoEntity;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

@Component
public class PedidoMapper {
	
	@Autowired
	ClienteMapper mapper;
	
	public PedidoEntity toEntity(PedidoDTO dto) {
		PedidoEntity entity = new PedidoEntity();
		entity.setNumeroDoPedido(dto.getNumeroDoPedido());
		entity.setValorTotalDoPedido(dto.getValorTotalDoPedido());
		entity.setDataDoPedido(dto.getDataDoPedido());
		
		return entity;
	}
	
	public PedidoDTO toDTO(PedidoEntity entity) {
		PedidoDTO dto = new PedidoDTO();
		dto.setNumeroDoPedido(entity.getNumeroDoPedido());
		dto.setValorTotalDoPedido(entity.getValorTotalDoPedido());
		dto.setDataDoPedido(entity.getDataDoPedido());
		
		return dto;
	}
	
	public PedidoDTOAll EntityToAll(PedidoEntity entity) {
		PedidoDTOAll dto = new PedidoDTOAll();
		dto.setNumeroDoPedido(entity.getNumeroDoPedido());
		dto.setValorTotalDoPedido(entity.getValorTotalDoPedido());
		dto.setDataDoPedido(entity.getDataDoPedido());
		dto.setDataEntrega(entity.getDataEntrega());
		dto.setStatus(entity.getStatus());
		
		return dto;
	}

	public PedidoDTOComp EntityToDTOComp(PedidoEntity entity) {
		PedidoDTOComp dto = new PedidoDTOComp();
		dto.setNumeroDoPedido(entity.getNumeroDoPedido());
		dto.setValorTotalDoPedido(entity.getValorTotalDoPedido());
		dto.setDataDoPedido(entity.getDataDoPedido());
		dto.setDataEntrega(entity.getDataEntrega());
		dto.setStatus(entity.getStatus());
		dto.setCliente(mapper.entityToDTO(entity.getCliente()));
		
		return dto;
	}
	
	
}