package org.serratec.ecommerce.services;

import java.util.List;

import org.serratec.ecommerce.dto.PedidoDTO;
import org.serratec.ecommerce.entities.PedidoEntity;
import org.serratec.ecommerce.entities.ProdutoEntity;
import org.serratec.ecommerce.entities.ProdutosPedidosEntity;
import org.serratec.ecommerce.exceptions.ProdutoNotFoundException;
import org.serratec.ecommerce.repositories.ProdutosPedidosRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

@Service
public class ProdutosPedidosService {

	@Autowired
	ProdutosPedidosRepository repository;

	@Autowired
	ProdutoService produtoService;

	public List<ProdutosPedidosEntity> findByPedido(PedidoEntity pedido) {
		return repository.findByPedido(pedido);
	}

	public List<ProdutosPedidosEntity> findByProduto(ProdutoEntity prodEntity) {
		return repository.findByProduto(prodEntity);
	}

	public ProdutosPedidosEntity findByPedidoAndProduto(PedidoEntity pedido, ProdutoEntity produto) {
		return repository.findByPedidoAndProduto(pedido, produto);
	}

	public ProdutosPedidosEntity create(PedidoEntity pedido, PedidoDTO dto) throws ProdutoNotFoundException {
		var produtosPedidos = new ProdutosPedidosEntity();
		produtosPedidos.setPedido(pedido);
		produtosPedidos.setQuantidade(dto.getQuantidade());
		ProdutoEntity produto = produtoService.findByNome(dto.getProduto());
		produtosPedidos.setProduto(produto);
		produtosPedidos.setPreco(produto.getPreco());
		return repository.save(produtosPedidos);
	}

	public ProdutosPedidosEntity update(ProdutosPedidosEntity entity, Integer quantidade) {
		entity.setQuantidade(quantidade);
		return repository.save(entity);
	}

	public void delete(ProdutosPedidosEntity entity) {
		repository.delete(entity);
	}

}