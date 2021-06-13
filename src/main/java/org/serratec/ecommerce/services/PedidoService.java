package org.serratec.ecommerce.services;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import javax.mail.MessagingException;

import org.serratec.ecommerce.dto.PedidoDTO;
import org.serratec.ecommerce.dto.PedidoDTOAll;
import org.serratec.ecommerce.dto.PedidoDTOComp;
import org.serratec.ecommerce.dto.ProdutoDTOUsuario;
import org.serratec.ecommerce.dto.ProdutosPedidosDTO;
import org.serratec.ecommerce.entities.ClienteEntity;
import org.serratec.ecommerce.entities.PedidoEntity;
import org.serratec.ecommerce.entities.ProdutosPedidosEntity;
import org.serratec.ecommerce.enums.StatusEnum;
import org.serratec.ecommerce.exceptions.ClienteNotFoundException;
import org.serratec.ecommerce.exceptions.EnderecoNotFoundException;
import org.serratec.ecommerce.exceptions.EstoqueInsuficienteException;
import org.serratec.ecommerce.exceptions.PedidoFinalizadoException;
import org.serratec.ecommerce.exceptions.PedidoNotFoundException;
import org.serratec.ecommerce.exceptions.ProdutoNotFoundException;
import org.serratec.ecommerce.exceptions.StatusUnacceptableException;
import org.serratec.ecommerce.exceptions.ValorNegativoException;
import org.serratec.ecommerce.mapper.PedidoMapper;
import org.serratec.ecommerce.mapper.ProdutoMapper;
import org.serratec.ecommerce.repositories.PedidoRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestTemplate;

@Service
public class PedidoService {
	
	@Autowired
	PedidoRepository repository;
	
	@Autowired
	PedidoMapper mapper;
	
	@Autowired
	ProdutoService produtoService;
	
	@Autowired
	ProdutosPedidosService produtosPedidosService;
	
	@Autowired
	ClienteService clienteService;
	
	@Autowired
	EmailSenderService emailSender;
	
	@Autowired
	EnderecoService endService;
	
	@Autowired
	ProdutoMapper prodMapper;
	
	public List<PedidoDTOAll> getAll() {
		return repository.findAll(Sort.by("dataDoPedido")).stream().map(mapper::entityToAll).collect(Collectors.toList());
	}
	
	public List<PedidoDTOAll> getByCliente(String userName) throws ClienteNotFoundException {
		ClienteEntity cliente = clienteService.findByUserNameOrEmail(userName);
		List<PedidoEntity> listaPedido = repository.findByCliente(cliente);
		List<PedidoDTOAll> listaDTO = new ArrayList<>();
		for (PedidoEntity pedidoEntity : listaPedido) {
			listaDTO.add(mapper.entityToAll(pedidoEntity));
		}
		return listaDTO;
	}
	
	public PedidoDTOComp getByNumeroDTO(Long numeroDoPedido) throws PedidoNotFoundException {
		Optional<PedidoEntity> pedido = repository.findByNumeroDoPedido(numeroDoPedido);
		if (pedido.isEmpty()) {
			throw new PedidoNotFoundException("Não existe pedido com esse numero.");
		}
		PedidoDTOComp comp = mapper.entityToDTOComp(pedido.get());
		
		ArrayList<ProdutosPedidosDTO> listaProduto = new ArrayList<>();
		
		List<ProdutosPedidosEntity> produtosPedidos = produtosPedidosService.findByPedido(pedido.get());
		for (ProdutosPedidosEntity produtosPedidosEntity : produtosPedidos) {
			var produtosPedidosDTO = new ProdutosPedidosDTO();
			produtosPedidosDTO.setNome(produtosPedidosEntity.getProduto().getNome());
			produtosPedidosDTO.setValor(produtosPedidosEntity.getPreco());
			produtosPedidosDTO.setQuantidade(produtosPedidosEntity.getQuantidade());
			produtosPedidosDTO.setImagem(prodMapper.geraUrl(produtosPedidosDTO.getNome()));
			
			listaProduto.add(produtosPedidosDTO);
		}
		comp.setProduto(listaProduto);
		return comp;
	}
	
	public PedidoEntity getByNumero(Long numeroDoPedido) throws PedidoNotFoundException {
		Optional<PedidoEntity> pedido = repository.findByNumeroDoPedido(numeroDoPedido);
		if (pedido.isEmpty()) {
			throw new PedidoNotFoundException("Não existe pedido com esse numero.");
		}
		return pedido.get();
	}
	
	public String create(PedidoDTO pedidoNovo) throws ProdutoNotFoundException, ClienteNotFoundException, EnderecoNotFoundException, ValorNegativoException {
		this.verificaQuantidade(pedidoNovo.getQuantidade());
		PedidoEntity pedido = mapper.toEntity(pedidoNovo);
		ClienteEntity cliente = clienteService.findByUserNameOrEmail(pedidoNovo.getCliente());
		pedido.setCliente(cliente);
		pedido.setDataDoPedido(LocalDate.now());
		pedido.setStatus(StatusEnum.RECEBIDO);
		pedido = repository.save(pedido);
		var produtosPedidos = produtosPedidosService.create(pedido, pedidoNovo);
		pedido.setTotalProdutos(produtosPedidos.getPreco() * produtosPedidos.getQuantidade());
		repository.save(this.sedex(pedido));
		return "Criado com sucesso";
	}
	
	public String update(PedidoDTO pedido) throws PedidoNotFoundException, ProdutoNotFoundException, EstoqueInsuficienteException, StatusUnacceptableException, EnderecoNotFoundException, PedidoFinalizadoException, ValorNegativoException {
		this.verificaQuantidade(pedido.getQuantidade());
		var pedidoEntity = getByNumero(pedido.getNumeroDoPedido());
		if (pedidoEntity.getStatus() == StatusEnum.RECEBIDO) {
			if (pedido.getProduto() != null) {
				var produtoEntity = produtoService.findByNome(pedido.getProduto());
				Optional<ProdutosPedidosEntity> produtosPedidos = Optional.ofNullable(produtosPedidosService.findByPedidoAndProduto(pedidoEntity, produtoEntity));
				if (produtosPedidos.isPresent()) {
					if (pedido.getQuantidade() <= produtoEntity.getQuantEstoque()) {
						pedidoEntity.setTotalProdutos(pedidoEntity.getTotalProdutos() + ((pedido.getQuantidade() - produtosPedidos.get().getQuantidade()) * produtosPedidos.get().getPreco()));
						pedidoEntity.setValorTotalDoPedido(pedidoEntity.getTotalProdutos() + pedidoEntity.getFrete());
						produtosPedidosService.update(produtosPedidos.get(), pedido.getQuantidade());
						List<ProdutosPedidosEntity> listaPedProd = produtosPedidosService.findByPedido(pedidoEntity);
						if(listaPedProd.isEmpty()){
							repository.delete(pedidoEntity);
							return "Pedido sem produtos. Pedido deletado!";
						}
						repository.save(pedidoEntity);
						return "Atualizado com sucesso!";
					} else throw new EstoqueInsuficienteException("Estoque insuficiente!");
				} else if (pedido.getQuantidade() <= produtoEntity.getQuantEstoque()) {
					var produtosPedidosNovo = produtosPedidosService.create(pedidoEntity, pedido);
					pedidoEntity.setTotalProdutos(pedidoEntity.getTotalProdutos() + produtosPedidosNovo.getPreco() * produtosPedidosNovo.getQuantidade());
					pedidoEntity.setValorTotalDoPedido(pedidoEntity.getTotalProdutos() + pedidoEntity.getFrete());
				}
			}
			if (pedido.getEndEntrega() != null) {
				pedidoEntity.setEndEntrega(pedido.getEndEntrega());
				pedidoEntity.setValorTotalDoPedido(pedidoEntity.getValorTotalDoPedido() - pedidoEntity.getFrete());
				pedidoEntity = this.sedex(pedidoEntity);
			}
			repository.save(pedidoEntity);
			return "Atualizado com sucesso!";
		}
		throw new StatusUnacceptableException("Apenas pedidos recebidos podem ser editados.");
	}
	
	public String fechar(long numeroPedido) throws PedidoNotFoundException, EstoqueInsuficienteException, StatusUnacceptableException, MessagingException {
		PedidoEntity pedido = this.getByNumero(numeroPedido);
		if (pedido.getStatus() == StatusEnum.RECEBIDO) {
			var tableImage1 = "<tr><td align=\"left\" style=\"padding:0;Margin:0;width:270px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td style=\"padding:0;Margin:0;font-size:0px\" align=\"center\"><img class=\"adapt-img\" src=\"";
			var tableImage2 = "\" alt style=\"display:block;border:0;outline:none;text-decoration:none;-ms-interpolation-mode:bicubic\" width=\"150\"></td>\r\n"
					+ "</tr><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-top:10px\"><h3 style=\"Margin:0;line-height:24px;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;font-size:20px;font-style:normal;font-weight:normal;color:#333333\">";
			var tableImage3 = "<br></h3></td></tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:5px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Quantidade: ";
			var tableImage4 = "</p></td>\r\n"
					+ "</tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:5px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Valor: ";
			var tableImage5 = "</p></td></tr></table></td></tr>";
			var tabelaProdutos = new StringBuilder();
			List<ProdutosPedidosEntity> listaProdutosPedidos = produtosPedidosService.findByPedido(pedido);
			for (ProdutosPedidosEntity produtosPedidosEntity : listaProdutosPedidos) {
				produtoService.vender(produtosPedidosEntity.getProduto(), produtosPedidosEntity.getQuantidade());
				ProdutoDTOUsuario produto = prodMapper.entityToDTOUsuario(produtosPedidosEntity.getProduto());
				tabelaProdutos.append(tableImage1 + prodMapper.geraUrl(produto.getNome()) + tableImage2 + produto.getNome() + tableImage3 + produtosPedidosEntity.getPreco() + tableImage4 + produtosPedidosEntity.getQuantidade() + tableImage5);
			}
			pedido.setStatus(StatusEnum.FECHADO);
			repository.save(pedido);
			var corpo = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:o=\"urn:schemas-microsoft-com:office:office\"><head><meta charset=\"UTF-8\"><meta content=\"width=device-width, initial-scale=1\" name=\"viewport\"><meta name=\"x-apple-disable-message-reformatting\"><meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\"><meta content=\"telephone=no\" name=\"format-detection\"><title>Compra efetuada</title> <!--[if (mso 16)]><style type=\"text/css\">     a {text-decoration: none;}     </style><![endif]--> <!--[if gte mso 9]><style>sup { font-size: 100% !important; }</style><![endif]--> <!--[if gte mso 9]><xml> <o:OfficeDocumentSettings> <o:AllowPNG></o:AllowPNG> <o:PixelsPerInch>96</o:PixelsPerInch> </o:OfficeDocumentSettings> </xml><![endif]--><style type=\"text/css\">#outlook a {	padding:0;}.es-button {	mso-style-priority:100!important;	text-decoration:none!important;}a[x-apple-data-detectors] {	color:inherit!important;	text-decoration:none!important;	font-size:inherit!important;	font-family:inherit!important;	font-weight:inherit!important;	line-height:inherit!important;}.es-desk-hidden {	display:none;	float:left;	overflow:hidden;	width:0;	max-height:0;	line-height:0;	mso-hide:all;}[data-ogsb] .es-button {	border-width:0!important;	padding:10px 20px 10px 20px!important;}@media only screen and (max-width:600px) {p, ul li, ol li, a { line-height:150%!important } h1 { font-size:30px!important; text-align:center; line-height:120%!important } h2 { font-size:26px!important; text-align:center; line-height:120%!important } h3 { font-size:20px!important; text-align:center; line-height:120%!important } .es-header-body h1 a, .es-content-body h1 a, .es-footer-body h1 a { font-size:30px!important } .es-header-body h2 a, .es-content-body h2 a, .es-footer-body h2 a { font-size:26px!important } .es-header-body h3 a, .es-content-body h3 a, .es-footer-body h3 a { font-size:20px!important } .es-menu td a { font-size:16px!important } .es-header-body p, .es-header-body ul li, .es-header-body ol li, .es-header-body a { font-size:16px!important } .es-content-body p, .es-content-body ul li, .es-content-body ol li, .es-content-body a { font-size:16px!important } .es-footer-body p, .es-footer-body ul li, .es-footer-body ol li, .es-footer-body a { font-size:16px!important } .es-infoblock p, .es-infoblock ul li, .es-infoblock ol li, .es-infoblock a { font-size:12px!important } *[class=\"gmail-fix\"] { display:none!important } .es-m-txt-c, .es-m-txt-c h1, .es-m-txt-c h2, .es-m-txt-c h3 { text-align:center!important } .es-m-txt-r, .es-m-txt-r h1, .es-m-txt-r h2, .es-m-txt-r h3 { text-align:right!important } .es-m-txt-l, .es-m-txt-l h1, .es-m-txt-l h2, .es-m-txt-l h3 { text-align:left!important } .es-m-txt-r img, .es-m-txt-c img, .es-m-txt-l img { display:inline!important } .es-button-border { display:block!important } a.es-button, button.es-button { font-size:20px!important; display:block!important; border-width:10px 0px 10px 0px!important } .es-adaptive table, .es-left, .es-right { width:100%!important } .es-content table, .es-header table, .es-footer table, .es-content, .es-footer, .es-header { width:100%!important; max-width:600px!important } .es-adapt-td { display:block!important; width:100%!important } .adapt-img { width:100%!important; height:auto!important } .es-m-p0 { padding:0px!important } .es-m-p0r { padding-right:0px!important } .es-m-p0l { padding-left:0px!important } .es-m-p0t { padding-top:0px!important } .es-m-p0b { padding-bottom:0!important } .es-m-p20b { padding-bottom:20px!important } .es-mobile-hidden, .es-hidden { display:none!important } tr.es-desk-hidden, td.es-desk-hidden, table.es-desk-hidden { width:auto!important; overflow:visible!important; float:none!important; max-height:inherit!important; line-height:inherit!important } tr.es-desk-hidden { display:table-row!important } table.es-desk-hidden { display:table!important } td.es-desk-menu-hidden { display:table-cell!important } .es-menu td { width:1%!important } table.es-table-not-adapt, .esd-block-html table { width:auto!important } table.es-social { display:inline-block!important } table.es-social td { display:inline-block!important } }</style></head>\r\n"
					+ "<body style=\"width:100%;font-family:arial, 'helvetica neue', helvetica, sans-serif;-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%;padding:0;Margin:0\"><div class=\"es-wrapper-color\" style=\"background-color:#F6F6F6\"> <!--[if gte mso 9]><v:background xmlns:v=\"urn:schemas-microsoft-com:vml\" fill=\"t\"> <v:fill type=\"tile\" color=\"#f6f6f6\"></v:fill> </v:background><![endif]--><table class=\"es-wrapper\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;padding:0;Margin:0;width:100%;height:100%;background-repeat:repeat;background-position:center top\"><tr><td valign=\"top\" style=\"padding:0;Margin:0\"><table class=\"es-content\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;table-layout:fixed !important;width:100%\"><tr><td align=\"center\" style=\"padding:0;Margin:0\"><table class=\"es-content-body\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;background-color:#FFFFFF;width:600px\"><tr><td align=\"left\" style=\"padding:20px;Margin:0\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td valign=\"top\" align=\"center\" style=\"padding:0;Margin:0;width:560px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-bottom:15px\"><h2 style=\"Margin:0;line-height:29px;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;font-size:24px;font-style:normal;font-weight:normal;color:#333333\">Obrigado por comprar com a gente!<br></h2>\r\n"
					+ "</td></tr><tr><td style=\"padding:0;Margin:0;font-size:0px\" align=\"center\"><img class=\"adapt-img\" src=\"https://prghxj.stripocdn.email/content/guids/CABINET_3ef18c19ee5479b4c800926df0c08e63/images/35331623515864588.png\" alt style=\"display:block;border:0;outline:none;text-decoration:none;-ms-interpolation-mode:bicubic\" width=\"560\"></td></tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:20px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Seu pedido está sendo computado e aguardando a confirmação de compra.<br></p></td></tr></table></td></tr></table></td>\r\n"
					+ "</tr><tr><td align=\"left\" style=\"Margin:0;padding-top:20px;padding-bottom:20px;padding-left:20px;padding-right:20px\"> <!--[if mso]><table style=\"width:560px\"><tr><td style=\"width:270px\" valign=\"top\"><![endif]--><table class=\"es-left\" cellspacing=\"0\" cellpadding=\"0\" align=\"left\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;float:left\"><tr><td class=\"es-m-p20b\" align=\"left\" style=\"padding:0;Margin:0;width:270px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td align=\"left\" style=\"padding:0;Margin:0\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Pedido " + pedido.getNumeroDoPedido() + "<br></p></td>\r\n"
					+ "</tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:20px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Total: R$" + Double.toString(pedido.getTotalProdutos()).replace('.', ',') + "<br>Frete: R$" + Double.toString(pedido.getFrete()).replace('.', ',') + "<br>Valor Final: R$" + Double.toString(pedido.getValorTotalDoPedido()).replace('.', ',') + "<br></p></td></tr></table></td></tr></table> <!--[if mso]></td><td style=\"width:20px\"></td>\r\n"
					+ "<td style=\"width:270px\" valign=\"top\"><![endif]--><table class=\"es-right\" cellspacing=\"0\" cellpadding=\"0\" align=\"right\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;float:right\">" + tabelaProdutos.toString() + "</table> <!--[if mso]></td></tr></table><![endif]--></td></tr></table></td>\r\n"
					+ "</tr></table><table class=\"es-footer\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;table-layout:fixed !important;width:100%;background-color:transparent;background-repeat:repeat;background-position:center top\"><tr><td align=\"center\" style=\"padding:0;Margin:0\"><table class=\"es-footer-body\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;background-color:transparent;width:600px\"><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:20px;padding-left:20px;padding-right:20px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td valign=\"top\" align=\"center\" style=\"padding:0;Margin:0;width:560px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td style=\"padding:20px;Margin:0;font-size:0\" align=\"center\"><table width=\"75%\" height=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td style=\"padding:0;Margin:0;border-bottom:1px solid #CCCCCC;background:none;height:1px;width:100%;margin:0px\"></td>\r\n"
					+ "</tr></table></td></tr><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-top:10px;padding-bottom:10px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:17px;color:#333333;font-size:11px\">Todos os direitos reservados.<br></p></td></tr><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-top:10px;padding-bottom:10px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:17px;color:#333333;font-size:11px\">© 2021 Grupo 6<br></p></td></tr></table></td></tr></table></td></tr></table></td></tr></table></td></tr></table></div></body></html>";
			emailSender.sendSimpleMessage(pedido.getCliente().getEmail(), "Pedido " + pedido.getNumeroDoPedido() + " fechado com sucesso", corpo);
			return "Fechado com sucesso!";
		}
		throw new StatusUnacceptableException("Apenas pedidos recebidos podem ser fechados.");
	}
	
	public String pagar(Long numeroDoPedido) throws PedidoNotFoundException, StatusUnacceptableException, EnderecoNotFoundException, MessagingException {
		PedidoEntity pedido = this.getByNumero(numeroDoPedido);
		if (pedido.getStatus() == StatusEnum.FECHADO) {
			pedido.setDataDoPedido(LocalDate.now());
			pedido.setStatus(StatusEnum.PAGO);
			repository.save(this.sedexPrazo(pedido));
			emailSender.sendSimpleMessage(pedido.getCliente().getEmail(), "Recebemos seu pagamento", "Recebemos seu pagamento, em breve seus produtos serão enviados à transportadora.");
			return "Pagamento Recebido!";
		}
		throw new StatusUnacceptableException("Favor fechar o pedido antes de efetuar pagamento!"); 
	}
	
	public String transportar(Long numeroDoPedido) throws PedidoNotFoundException, StatusUnacceptableException, MessagingException {
		PedidoEntity pedido = this.getByNumero(numeroDoPedido);
		if (pedido.getStatus() == StatusEnum.PAGO) {
			pedido.setStatus(StatusEnum.TRANSPORTE);
			repository.save(pedido);
			var corpo = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:o=\"urn:schemas-microsoft-com:office:office\"><head><meta charset=\"UTF-8\"><meta content=\"width=device-width, initial-scale=1\" name=\"viewport\"><meta name=\"x-apple-disable-message-reformatting\"><meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\"><meta content=\"telephone=no\" name=\"format-detection\"><title>Transporte</title> <!--[if (mso 16)]><style type=\"text/css\">     a {text-decoration: none;}     </style><![endif]--> <!--[if gte mso 9]><style>sup { font-size: 100% !important; }</style><![endif]--> <!--[if gte mso 9]><xml> <o:OfficeDocumentSettings> <o:AllowPNG></o:AllowPNG> <o:PixelsPerInch>96</o:PixelsPerInch> </o:OfficeDocumentSettings> </xml><![endif]--><style type=\"text/css\">#outlook a {	padding:0;}.es-button {	mso-style-priority:100!important;	text-decoration:none!important;}a[x-apple-data-detectors] {	color:inherit!important;	text-decoration:none!important;	font-size:inherit!important;	font-family:inherit!important;	font-weight:inherit!important;	line-height:inherit!important;}.es-desk-hidden {	display:none;	float:left;	overflow:hidden;	width:0;	max-height:0;	line-height:0;	mso-hide:all;}[data-ogsb] .es-button {	border-width:0!important;	padding:10px 20px 10px 20px!important;}@media only screen and (max-width:600px) {p, ul li, ol li, a { line-height:150%!important } h1 { font-size:30px!important; text-align:center; line-height:120%!important } h2 { font-size:26px!important; text-align:center; line-height:120%!important } h3 { font-size:20px!important; text-align:center; line-height:120%!important } .es-header-body h1 a, .es-content-body h1 a, .es-footer-body h1 a { font-size:30px!important } .es-header-body h2 a, .es-content-body h2 a, .es-footer-body h2 a { font-size:26px!important } .es-header-body h3 a, .es-content-body h3 a, .es-footer-body h3 a { font-size:20px!important } .es-menu td a { font-size:16px!important } .es-header-body p, .es-header-body ul li, .es-header-body ol li, .es-header-body a { font-size:16px!important } .es-content-body p, .es-content-body ul li, .es-content-body ol li, .es-content-body a { font-size:16px!important } .es-footer-body p, .es-footer-body ul li, .es-footer-body ol li, .es-footer-body a { font-size:16px!important } .es-infoblock p, .es-infoblock ul li, .es-infoblock ol li, .es-infoblock a { font-size:12px!important } *[class=\"gmail-fix\"] { display:none!important } .es-m-txt-c, .es-m-txt-c h1, .es-m-txt-c h2, .es-m-txt-c h3 { text-align:center!important } .es-m-txt-r, .es-m-txt-r h1, .es-m-txt-r h2, .es-m-txt-r h3 { text-align:right!important } .es-m-txt-l, .es-m-txt-l h1, .es-m-txt-l h2, .es-m-txt-l h3 { text-align:left!important } .es-m-txt-r img, .es-m-txt-c img, .es-m-txt-l img { display:inline!important } .es-button-border { display:block!important } a.es-button, button.es-button { font-size:20px!important; display:block!important; border-width:10px 0px 10px 0px!important } .es-adaptive table, .es-left, .es-right { width:100%!important } .es-content table, .es-header table, .es-footer table, .es-content, .es-footer, .es-header { width:100%!important; max-width:600px!important } .es-adapt-td { display:block!important; width:100%!important } .adapt-img { width:100%!important; height:auto!important } .es-m-p0 { padding:0px!important } .es-m-p0r { padding-right:0px!important } .es-m-p0l { padding-left:0px!important } .es-m-p0t { padding-top:0px!important } .es-m-p0b { padding-bottom:0!important } .es-m-p20b { padding-bottom:20px!important } .es-mobile-hidden, .es-hidden { display:none!important } tr.es-desk-hidden, td.es-desk-hidden, table.es-desk-hidden { width:auto!important; overflow:visible!important; float:none!important; max-height:inherit!important; line-height:inherit!important } tr.es-desk-hidden { display:table-row!important } table.es-desk-hidden { display:table!important } td.es-desk-menu-hidden { display:table-cell!important } .es-menu td { width:1%!important } table.es-table-not-adapt, .esd-block-html table { width:auto!important } table.es-social { display:inline-block!important } table.es-social td { display:inline-block!important } }</style></head>\r\n"
					+ "<body style=\"width:100%;font-family:arial, 'helvetica neue', helvetica, sans-serif;-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%;padding:0;Margin:0\"><div class=\"es-wrapper-color\" style=\"background-color:#F6F6F6\"> <!--[if gte mso 9]><v:background xmlns:v=\"urn:schemas-microsoft-com:vml\" fill=\"t\"> <v:fill type=\"tile\" color=\"#f6f6f6\"></v:fill> </v:background><![endif]--><table class=\"es-wrapper\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;padding:0;Margin:0;width:100%;height:100%;background-repeat:repeat;background-position:center top\"><tr><td valign=\"top\" style=\"padding:0;Margin:0\"><table class=\"es-content\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;table-layout:fixed !important;width:100%\"><tr><td align=\"center\" style=\"padding:0;Margin:0\"><table class=\"es-content-body\" cellspacing=\"0\" cellpadding=\"0\" bgcolor=\"#ffffff\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;background-color:#FFFFFF;width:600px\"><tr><td align=\"left\" style=\"Margin:0;padding-top:20px;padding-bottom:20px;padding-left:20px;padding-right:20px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td valign=\"top\" align=\"center\" style=\"padding:0;Margin:0;width:560px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-bottom:15px\"><h2 style=\"Margin:0;line-height:29px;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;font-size:24px;font-style:normal;font-weight:normal;color:#333333\">Seu pedido está em transporte!<br></h2>\r\n"
					+ "</td></tr><tr><td style=\"padding:0;Margin:0;font-size:0px\" align=\"center\"><img class=\"adapt-img\" src=\"https://prghxj.stripocdn.email/content/guids/CABINET_3ef18c19ee5479b4c800926df0c08e63/images/35331623515864588.png\" alt style=\"display:block;border:0;outline:none;text-decoration:none;-ms-interpolation-mode:bicubic\" width=\"560\"></td></tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:20px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Olá " + pedido.getCliente().getNome() + ",<br></p></td></tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:15px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Temos uma ótima notícia, seu pedido está a caminho! Previsão de entrega: " + pedido.getDataEntrega() + "<br></p></td>\r\n"
					+ "</tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:20px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Segue o <span style=\"color:#FF8C00\"><em><u>link</u></em></span> para acompanhá-lo.</p></td></tr></table></td></tr></table></td></tr></table></td>\r\n"
					+ "</tr></table><table class=\"es-footer\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;table-layout:fixed !important;width:100%;background-color:transparent;background-repeat:repeat;background-position:center top\"><tr><td align=\"center\" style=\"padding:0;Margin:0\"><table class=\"es-footer-body\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;background-color:transparent;width:600px\"><tr><td align=\"left\" style=\"Margin:0;padding-top:20px;padding-bottom:20px;padding-left:20px;padding-right:20px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td valign=\"top\" align=\"center\" style=\"padding:0;Margin:0;width:560px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td style=\"padding:20px;Margin:0;font-size:0\" align=\"center\"><table width=\"75%\" height=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td style=\"padding:0;Margin:0;border-bottom:1px solid #CCCCCC;background:none;height:1px;width:100%;margin:0px\"></td>\r\n"
					+ "</tr></table></td></tr><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-top:10px;padding-bottom:10px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:17px;color:#333333;font-size:11px\">Todos os direitos reservados.<br></p></td></tr><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-top:10px;padding-bottom:10px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:17px;color:#333333;font-size:11px\">© 2021 Grupo 6<br></p></td></tr></table></td></tr></table></td></tr></table></td></tr></table></td></tr></table></div></body></html>";
			emailSender.sendSimpleMessage(pedido.getCliente().getEmail(), "Pedido " + pedido.getNumeroDoPedido() + " está a caminho", corpo);
			return "Pedido em transporte!";
		}
		throw new StatusUnacceptableException("Apenas pedidos pagos podem ser enviados para transporte.");
	}
	
	public String entregar(Long numeroDoPedido) throws PedidoNotFoundException, StatusUnacceptableException, MessagingException {
		PedidoEntity pedido = this.getByNumero(numeroDoPedido);
		if (pedido.getStatus() == StatusEnum.TRANSPORTE) {
			pedido.setStatus(StatusEnum.ENTREGUE);
			repository.save(pedido);
			var corpo = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:o=\"urn:schemas-microsoft-com:office:office\"><head><meta charset=\"UTF-8\"><meta content=\"width=device-width, initial-scale=1\" name=\"viewport\"><meta name=\"x-apple-disable-message-reformatting\"><meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\"><meta content=\"telephone=no\" name=\"format-detection\"><title>Entregue</title> <!--[if (mso 16)]><style type=\"text/css\">     a {text-decoration: none;}     </style><![endif]--> <!--[if gte mso 9]><style>sup { font-size: 100% !important; }</style><![endif]--> <!--[if gte mso 9]><xml> <o:OfficeDocumentSettings> <o:AllowPNG></o:AllowPNG> <o:PixelsPerInch>96</o:PixelsPerInch> </o:OfficeDocumentSettings> </xml><![endif]--><style type=\"text/css\">#outlook a {	padding:0;}.es-button {	mso-style-priority:100!important;	text-decoration:none!important;}a[x-apple-data-detectors] {	color:inherit!important;	text-decoration:none!important;	font-size:inherit!important;	font-family:inherit!important;	font-weight:inherit!important;	line-height:inherit!important;}.es-desk-hidden {	display:none;	float:left;	overflow:hidden;	width:0;	max-height:0;	line-height:0;	mso-hide:all;}[data-ogsb] .es-button {	border-width:0!important;	padding:10px 20px 10px 20px!important;}@media only screen and (max-width:600px) {p, ul li, ol li, a { line-height:150%!important } h1 { font-size:30px!important; text-align:center; line-height:120%!important } h2 { font-size:26px!important; text-align:center; line-height:120%!important } h3 { font-size:20px!important; text-align:center; line-height:120%!important } .es-header-body h1 a, .es-content-body h1 a, .es-footer-body h1 a { font-size:30px!important } .es-header-body h2 a, .es-content-body h2 a, .es-footer-body h2 a { font-size:26px!important } .es-header-body h3 a, .es-content-body h3 a, .es-footer-body h3 a { font-size:20px!important } .es-menu td a { font-size:16px!important } .es-header-body p, .es-header-body ul li, .es-header-body ol li, .es-header-body a { font-size:16px!important } .es-content-body p, .es-content-body ul li, .es-content-body ol li, .es-content-body a { font-size:16px!important } .es-footer-body p, .es-footer-body ul li, .es-footer-body ol li, .es-footer-body a { font-size:16px!important } .es-infoblock p, .es-infoblock ul li, .es-infoblock ol li, .es-infoblock a { font-size:12px!important } *[class=\"gmail-fix\"] { display:none!important } .es-m-txt-c, .es-m-txt-c h1, .es-m-txt-c h2, .es-m-txt-c h3 { text-align:center!important } .es-m-txt-r, .es-m-txt-r h1, .es-m-txt-r h2, .es-m-txt-r h3 { text-align:right!important } .es-m-txt-l, .es-m-txt-l h1, .es-m-txt-l h2, .es-m-txt-l h3 { text-align:left!important } .es-m-txt-r img, .es-m-txt-c img, .es-m-txt-l img { display:inline!important } .es-button-border { display:block!important } a.es-button, button.es-button { font-size:20px!important; display:block!important; border-width:10px 0px 10px 0px!important } .es-adaptive table, .es-left, .es-right { width:100%!important } .es-content table, .es-header table, .es-footer table, .es-content, .es-footer, .es-header { width:100%!important; max-width:600px!important } .es-adapt-td { display:block!important; width:100%!important } .adapt-img { width:100%!important; height:auto!important } .es-m-p0 { padding:0px!important } .es-m-p0r { padding-right:0px!important } .es-m-p0l { padding-left:0px!important } .es-m-p0t { padding-top:0px!important } .es-m-p0b { padding-bottom:0!important } .es-m-p20b { padding-bottom:20px!important } .es-mobile-hidden, .es-hidden { display:none!important } tr.es-desk-hidden, td.es-desk-hidden, table.es-desk-hidden { width:auto!important; overflow:visible!important; float:none!important; max-height:inherit!important; line-height:inherit!important } tr.es-desk-hidden { display:table-row!important } table.es-desk-hidden { display:table!important } td.es-desk-menu-hidden { display:table-cell!important } .es-menu td { width:1%!important } table.es-table-not-adapt, .esd-block-html table { width:auto!important } table.es-social { display:inline-block!important } table.es-social td { display:inline-block!important } }</style></head>\r\n"
					+ "<body style=\"width:100%;font-family:arial, 'helvetica neue', helvetica, sans-serif;-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%;padding:0;Margin:0\"><div class=\"es-wrapper-color\" style=\"background-color:#F6F6F6\"> <!--[if gte mso 9]><v:background xmlns:v=\"urn:schemas-microsoft-com:vml\" fill=\"t\"> <v:fill type=\"tile\" color=\"#f6f6f6\"></v:fill> </v:background><![endif]--><table class=\"es-wrapper\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;padding:0;Margin:0;width:100%;height:100%;background-repeat:repeat;background-position:center top\"><tr><td valign=\"top\" style=\"padding:0;Margin:0\"><table class=\"es-content\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;table-layout:fixed !important;width:100%\"><tr><td align=\"center\" style=\"padding:0;Margin:0\"><table class=\"es-content-body\" cellspacing=\"0\" cellpadding=\"0\" bgcolor=\"#ffffff\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;background-color:#FFFFFF;width:600px\"><tr><td align=\"left\" style=\"Margin:0;padding-top:20px;padding-bottom:20px;padding-left:20px;padding-right:20px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td valign=\"top\" align=\"center\" style=\"padding:0;Margin:0;width:560px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-bottom:15px\"><h2 style=\"Margin:0;line-height:29px;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;font-size:24px;font-style:normal;font-weight:normal;color:#333333\">Seu pedido foi entregue com sucesso!<br></h2>\r\n"
					+ "</td></tr><tr><td style=\"padding:0;Margin:0;font-size:0px\" align=\"center\"><img class=\"adapt-img\" src=\"https://prghxj.stripocdn.email/content/guids/CABINET_3ef18c19ee5479b4c800926df0c08e63/images/35331623515864588.png\" alt style=\"display:block;border:0;outline:none;text-decoration:none;-ms-interpolation-mode:bicubic\" width=\"560\"></td></tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:20px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Olá " + pedido.getCliente().getNome() + ",<br></p></td></tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:15px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Recebemos a confirmação da entrega dos produtos do pedido de número " + pedido.getNumeroDoPedido() + ".<br></p></td>\r\n"
					+ "</tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:20px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Agradecemos pela sua preferência. Estamos à sua disposição!<br></p></td></tr></table></td></tr></table></td></tr></table></td>\r\n"
					+ "</tr></table><table class=\"es-footer\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;table-layout:fixed !important;width:100%;background-color:transparent;background-repeat:repeat;background-position:center top\"><tr><td align=\"center\" style=\"padding:0;Margin:0\"><table class=\"es-footer-body\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;background-color:transparent;width:600px\"><tr><td align=\"left\" style=\"Margin:0;padding-top:20px;padding-bottom:20px;padding-left:20px;padding-right:20px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td valign=\"top\" align=\"center\" style=\"padding:0;Margin:0;width:560px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td style=\"padding:20px;Margin:0;font-size:0\" align=\"center\"><table width=\"75%\" height=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td style=\"padding:0;Margin:0;border-bottom:1px solid #CCCCCC;background:none;height:1px;width:100%;margin:0px\"></td>\r\n"
					+ "</tr></table></td></tr><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-top:10px;padding-bottom:10px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:17px;color:#333333;font-size:11px\">Todos os direitos reservados.<br></p></td></tr><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-top:10px;padding-bottom:10px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:17px;color:#333333;font-size:11px\">© 2021 Grupo 6<br></p></td></tr></table></td></tr></table></td></tr></table></td></tr></table></td></tr></table></div></body></html>";
			emailSender.sendSimpleMessage(pedido.getCliente().getEmail(), "Pedido " + pedido.getNumeroDoPedido() + " entregue com sucesso", corpo);
			return "Pedido entregue!";
		}
		throw new StatusUnacceptableException("Apenas pedidos em transporte podem ser entregues.");
	}
	
	public String delete(Long numeroDoPedido) throws PedidoNotFoundException, PedidoFinalizadoException, EstoqueInsuficienteException, MessagingException {
		PedidoEntity pedido = this.getByNumero(numeroDoPedido);
		if(pedido.getStatus() == StatusEnum.ENTREGUE) throw new PedidoFinalizadoException("Pedidos finalizados nao podem ser deletados");
		if (pedido.getStatus() == StatusEnum.FECHADO) {
			List<ProdutosPedidosEntity> listaPedProd = produtosPedidosService.findByPedido(pedido);
			for (ProdutosPedidosEntity produtosPedidosEntity : listaPedProd) {
				produtoService.retornaEstoque(produtosPedidosEntity.getProduto(), produtosPedidosEntity.getQuantidade());
				produtosPedidosService.delete(produtosPedidosEntity);
			}
			repository.delete(pedido);
			return "Pedido deletado com sucesso!";
		}
		if (pedido.getStatus() == StatusEnum.RECEBIDO) {
			List<ProdutosPedidosEntity> listaPedProd = produtosPedidosService.findByPedido(pedido);
			for (ProdutosPedidosEntity produtosPedidosEntity : listaPedProd) {
				produtosPedidosService.delete(produtosPedidosEntity);
			}
			repository.delete(pedido);
			return "Pedido deletado com sucesso!";
		}
		if (pedido.getStatus() != StatusEnum.CANCELADO) {
			List<ProdutosPedidosEntity> listaPedProd = produtosPedidosService.findByPedido(pedido);
			for (ProdutosPedidosEntity produtosPedidosEntity : listaPedProd) {
				produtoService.retornaEstoque(produtosPedidosEntity.getProduto(), produtosPedidosEntity.getQuantidade());
			}
			pedido.setStatus(StatusEnum.CANCELADO);
			repository.save(pedido);
			var corpo = "<!DOCTYPE html PUBLIC \"-//W3C//DTD XHTML 1.0 Transitional//EN\" \"http://www.w3.org/TR/xhtml1/DTD/xhtml1-transitional.dtd\"><html xmlns=\"http://www.w3.org/1999/xhtml\" xmlns:o=\"urn:schemas-microsoft-com:office:office\"><head><meta charset=\"UTF-8\"><meta content=\"width=device-width, initial-scale=1\" name=\"viewport\"><meta name=\"x-apple-disable-message-reformatting\"><meta http-equiv=\"X-UA-Compatible\" content=\"IE=edge\"><meta content=\"telephone=no\" name=\"format-detection\"><title>Pedido Cancelado</title> <!--[if (mso 16)]><style type=\"text/css\">     a {text-decoration: none;}     </style><![endif]--> <!--[if gte mso 9]><style>sup { font-size: 100% !important; }</style><![endif]--> <!--[if gte mso 9]><xml> <o:OfficeDocumentSettings> <o:AllowPNG></o:AllowPNG> <o:PixelsPerInch>96</o:PixelsPerInch> </o:OfficeDocumentSettings> </xml><![endif]--><style type=\"text/css\">#outlook a {	padding:0;}.es-button {	mso-style-priority:100!important;	text-decoration:none!important;}a[x-apple-data-detectors] {	color:inherit!important;	text-decoration:none!important;	font-size:inherit!important;	font-family:inherit!important;	font-weight:inherit!important;	line-height:inherit!important;}.es-desk-hidden {	display:none;	float:left;	overflow:hidden;	width:0;	max-height:0;	line-height:0;	mso-hide:all;}[data-ogsb] .es-button {	border-width:0!important;	padding:10px 20px 10px 20px!important;}@media only screen and (max-width:600px) {p, ul li, ol li, a { line-height:150%!important } h1 { font-size:30px!important; text-align:center; line-height:120%!important } h2 { font-size:26px!important; text-align:center; line-height:120%!important } h3 { font-size:20px!important; text-align:center; line-height:120%!important } .es-header-body h1 a, .es-content-body h1 a, .es-footer-body h1 a { font-size:30px!important } .es-header-body h2 a, .es-content-body h2 a, .es-footer-body h2 a { font-size:26px!important } .es-header-body h3 a, .es-content-body h3 a, .es-footer-body h3 a { font-size:20px!important } .es-menu td a { font-size:16px!important } .es-header-body p, .es-header-body ul li, .es-header-body ol li, .es-header-body a { font-size:16px!important } .es-content-body p, .es-content-body ul li, .es-content-body ol li, .es-content-body a { font-size:16px!important } .es-footer-body p, .es-footer-body ul li, .es-footer-body ol li, .es-footer-body a { font-size:16px!important } .es-infoblock p, .es-infoblock ul li, .es-infoblock ol li, .es-infoblock a { font-size:12px!important } *[class=\"gmail-fix\"] { display:none!important } .es-m-txt-c, .es-m-txt-c h1, .es-m-txt-c h2, .es-m-txt-c h3 { text-align:center!important } .es-m-txt-r, .es-m-txt-r h1, .es-m-txt-r h2, .es-m-txt-r h3 { text-align:right!important } .es-m-txt-l, .es-m-txt-l h1, .es-m-txt-l h2, .es-m-txt-l h3 { text-align:left!important } .es-m-txt-r img, .es-m-txt-c img, .es-m-txt-l img { display:inline!important } .es-button-border { display:block!important } a.es-button, button.es-button { font-size:20px!important; display:block!important; border-width:10px 0px 10px 0px!important } .es-adaptive table, .es-left, .es-right { width:100%!important } .es-content table, .es-header table, .es-footer table, .es-content, .es-footer, .es-header { width:100%!important; max-width:600px!important } .es-adapt-td { display:block!important; width:100%!important } .adapt-img { width:100%!important; height:auto!important } .es-m-p0 { padding:0px!important } .es-m-p0r { padding-right:0px!important } .es-m-p0l { padding-left:0px!important } .es-m-p0t { padding-top:0px!important } .es-m-p0b { padding-bottom:0!important } .es-m-p20b { padding-bottom:20px!important } .es-mobile-hidden, .es-hidden { display:none!important } tr.es-desk-hidden, td.es-desk-hidden, table.es-desk-hidden { width:auto!important; overflow:visible!important; float:none!important; max-height:inherit!important; line-height:inherit!important } tr.es-desk-hidden { display:table-row!important } table.es-desk-hidden { display:table!important } td.es-desk-menu-hidden { display:table-cell!important } .es-menu td { width:1%!important } table.es-table-not-adapt, .esd-block-html table { width:auto!important } table.es-social { display:inline-block!important } table.es-social td { display:inline-block!important } }</style></head>\r\n"
					+ "<body style=\"width:100%;font-family:arial, 'helvetica neue', helvetica, sans-serif;-webkit-text-size-adjust:100%;-ms-text-size-adjust:100%;padding:0;Margin:0\"><div class=\"es-wrapper-color\" style=\"background-color:#F6F6F6\"> <!--[if gte mso 9]><v:background xmlns:v=\"urn:schemas-microsoft-com:vml\" fill=\"t\"> <v:fill type=\"tile\" color=\"#f6f6f6\"></v:fill> </v:background><![endif]--><table class=\"es-wrapper\" width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;padding:0;Margin:0;width:100%;height:100%;background-repeat:repeat;background-position:center top\"><tr><td valign=\"top\" style=\"padding:0;Margin:0\"><table class=\"es-content\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;table-layout:fixed !important;width:100%\"><tr><td align=\"center\" style=\"padding:0;Margin:0\"><table class=\"es-content-body\" cellspacing=\"0\" cellpadding=\"0\" bgcolor=\"#ffffff\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;background-color:#FFFFFF;width:600px\"><tr><td align=\"left\" style=\"Margin:0;padding-top:20px;padding-bottom:20px;padding-left:20px;padding-right:20px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td valign=\"top\" align=\"center\" style=\"padding:0;Margin:0;width:560px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-bottom:15px\"><h2 style=\"Margin:0;line-height:29px;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;font-size:24px;font-style:normal;font-weight:normal;color:#333333\">Seu pedido foi cancelado!<br></h2>\r\n"
					+ "</td></tr><tr><td style=\"padding:0;Margin:0;font-size:0px\" align=\"center\"><img class=\"adapt-img\" src=\"https://prghxj.stripocdn.email/content/guids/CABINET_3ef18c19ee5479b4c800926df0c08e63/images/35331623515864588.png\" alt style=\"display:block;border:0;outline:none;text-decoration:none;-ms-interpolation-mode:bicubic\" width=\"560\"></td></tr><tr><td align=\"left\" style=\"padding:0;Margin:0;padding-top:15px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Olá, " + pedido.getCliente().getNome() + ",<br></p><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">Seu pedido nº " + pedido.getNumeroDoPedido() + " foi cancelado.</p>\r\n"
					+ "<p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:21px;color:#333333;font-size:14px\">O estorno do pagamento será realizado dentro de 2 dias úteis.<br></p></td></tr></table></td></tr></table></td></tr></table></td>\r\n"
					+ "</tr></table><table class=\"es-footer\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;table-layout:fixed !important;width:100%;background-color:transparent;background-repeat:repeat;background-position:center top\"><tr><td align=\"center\" style=\"padding:0;Margin:0\"><table class=\"es-footer-body\" cellspacing=\"0\" cellpadding=\"0\" align=\"center\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px;background-color:transparent;width:600px\"><tr><td align=\"left\" style=\"Margin:0;padding-top:20px;padding-bottom:20px;padding-left:20px;padding-right:20px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td valign=\"top\" align=\"center\" style=\"padding:0;Margin:0;width:560px\"><table width=\"100%\" cellspacing=\"0\" cellpadding=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td style=\"padding:20px;Margin:0;font-size:0\" align=\"center\"><table width=\"75%\" height=\"100%\" cellspacing=\"0\" cellpadding=\"0\" border=\"0\" role=\"presentation\" style=\"mso-table-lspace:0pt;mso-table-rspace:0pt;border-collapse:collapse;border-spacing:0px\"><tr><td style=\"padding:0;Margin:0;border-bottom:1px solid #CCCCCC;background:none;height:1px;width:100%;margin:0px\"></td>\r\n"
					+ "</tr></table></td></tr><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-top:10px;padding-bottom:10px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:17px;color:#333333;font-size:11px\">Todos os direitos reservados.<br></p></td></tr><tr><td align=\"center\" style=\"padding:0;Margin:0;padding-top:10px;padding-bottom:10px\"><p style=\"Margin:0;-webkit-text-size-adjust:none;-ms-text-size-adjust:none;mso-line-height-rule:exactly;font-family:arial, 'helvetica neue', helvetica, sans-serif;line-height:17px;color:#333333;font-size:11px\">© 2021 Grupo 06<br></p></td></tr></table></td></tr></table></td></tr></table></td></tr></table></td></tr></table></div></body></html>";
			emailSender.sendSimpleMessage(pedido.getCliente().getEmail(), "Pedido cancelado", corpo);
			return "Pedido cancelado com sucesso!";
		}
		return "Esse pedido esta cancelado e nao pode ser alterado!";
	}
	
	public PedidoEntity sedex(PedidoEntity pedido) throws EnderecoNotFoundException {
		var restTemplate = new RestTemplate();
		String cepDestino = endService.findByNomeAndCliente(pedido.getEndEntrega(), pedido.getCliente()).getCep();
		String sedex = restTemplate.getForObject("http://ws.correios.com.br/calculador/CalcPrecoPrazo.aspx?nCdEmpresa=08082650&sDsSenha=564321&sCepOrigem=25955050&sCepDestino="+cepDestino+"&nVlPeso=1&nCdFormato=1&nVlComprimento=20&nVlAltura=20&nVlLargura=20&sCdMaoPropria=n&nVlValorDeclarado=0&sCdAvisoRecebimento=n&nCdServico=40010&nVlDiametro=0&StrRetorno=xml&nIndicaCalculo=3", String.class);
		int valorInicio = sedex.indexOf("Valor>") + 6;
		int valorFim = sedex.indexOf("</Valor");
		pedido.setFrete(Double.parseDouble(sedex.substring(valorInicio, valorFim).replace(',', '.')));
		pedido.setValorTotalDoPedido(pedido.getTotalProdutos() + pedido.getFrete());
		int prazoInicio = sedex.indexOf("PrazoEntrega>") + 13;
		int prazoFim = sedex.indexOf("</PrazoEntrega>");
		pedido.setDataEntrega(pedido.getDataDoPedido().plusDays(Long.parseLong(sedex.substring(prazoInicio, prazoFim)) + 1));
		return pedido;
	}
	
	public PedidoEntity sedexPrazo(PedidoEntity pedido) throws EnderecoNotFoundException {
		var restTemplate = new RestTemplate();
		String cepDestino = endService.findByNomeAndCliente(pedido.getEndEntrega(), pedido.getCliente()).getCep();
		String sedex = restTemplate.getForObject("http://ws.correios.com.br/calculador/CalcPrecoPrazo.aspx?nCdEmpresa=08082650&sDsSenha=564321&sCepOrigem=25955050&sCepDestino="+cepDestino+"&nVlPeso=1&nCdFormato=1&nVlComprimento=20&nVlAltura=20&nVlLargura=20&sCdMaoPropria=n&nVlValorDeclarado=0&sCdAvisoRecebimento=n&nCdServico=40010&nVlDiametro=0&StrRetorno=xml&nIndicaCalculo=3", String.class);
		int prazoInicio = sedex.indexOf("PrazoEntrega>") + 13;
		int prazoFim = sedex.indexOf("</PrazoEntrega>");
		pedido.setDataEntrega(pedido.getDataDoPedido().plusDays(Long.parseLong(sedex.substring(prazoInicio, prazoFim)) + 1));
		return pedido;
	}
	private void verificaQuantidade(Integer quantidade) throws ValorNegativoException {
		if(quantidade < 0 ) {
			throw new ValorNegativoException("Não é possível inserir uma quantidade negativa!");
		}
		
	}
}